/*
 * Copyright (C) 2021 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.scanner.scanners.fossid

import java.io.File
import java.io.IOException
import java.net.Authenticator
import java.time.Instant

import kotlin.time.measureTimedValue

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

import org.ossreviewtoolkit.clients.fossid.checkDownloadStatus
import org.ossreviewtoolkit.clients.fossid.checkResponse
import org.ossreviewtoolkit.clients.fossid.createProject
import org.ossreviewtoolkit.clients.fossid.createScan
import org.ossreviewtoolkit.clients.fossid.deleteScan
import org.ossreviewtoolkit.clients.fossid.downloadFromGit
import org.ossreviewtoolkit.clients.fossid.getProject
import org.ossreviewtoolkit.clients.fossid.listIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listIgnoredFiles
import org.ossreviewtoolkit.clients.fossid.listMarkedAsIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listPendingFiles
import org.ossreviewtoolkit.clients.fossid.listScansForProject
import org.ossreviewtoolkit.clients.fossid.model.Project
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanStatus
import org.ossreviewtoolkit.clients.fossid.runScan
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.ScannerOptions
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.RemoteScanner
import org.ossreviewtoolkit.spdx.enumSetOf
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.showStackTrace
import org.ossreviewtoolkit.utils.toUri

/**
 * A wrapper for [FossID](https://fossid.com/).
 *
 * This scanner can be configured in [ScannerConfiguration.options]. For the options available and their documentation
 * refer to [FossIdConfig].
 */
class FossId internal constructor(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration,
    private val config: FossIdConfig
) : RemoteScanner(name, scannerConfig, downloaderConfig) {
    class Factory : AbstractScannerFactory<FossId>("FossId") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            FossId(scannerName, scannerConfig, downloaderConfig, FossIdConfig.create(scannerConfig))
    }

    companion object {
        @JvmStatic
        private val PROJECT_NAME_REGEX = Regex("""^.*\/([\w.\-]+)(?:\.git)?$""")

        @JvmStatic
        private val GIT_FETCH_DONE_REGEX = Regex("-> FETCH_HEAD(?: Already up to date.)*$")

        @JvmStatic
        private val WAIT_INTERVAL_MS = 10000L

        @JvmStatic
        private val WAIT_REPETITION = 360

        /**
         * The scan states for which a scan can be triggered.
         */
        @JvmStatic
        private val SCAN_STATE_FOR_TRIGGER = enumSetOf(ScanStatus.NOT_STARTED, ScanStatus.NEW)

        /**
         * Convert a Git repository URL to a valid project name, e.g.
         * https://github.com/jshttp/mime-types.git -> mime-types
         */
        fun convertGitUrlToProjectName(gitRepoUrl: String): String {
            val projectNameMatcher = PROJECT_NAME_REGEX.matchEntire(gitRepoUrl)

            requireNotNull(projectNameMatcher) { "Git repository URL '$gitRepoUrl' does not contain a project name." }

            val projectName = projectNameMatcher.groupValues[1]

            log.info { "Found project name in '$projectName' in URL '$gitRepoUrl'." }

            return projectName
        }

        /**
         * Generate a list of pairs to be passed as parameters when starting a new delta scan for [existingScancode].
         */
        internal fun deltaScanRunParameters(existingScancode: String): Array<Pair<String, String>> =
            arrayOf(
                "reuse_identification" to "1",
                "identification_reuse_type" to "specific_scan",
                "specific_code" to existingScancode
            )

        /**
         * This function fetches credentials for [repoUrl] and insert them between the URL scheme and the host. If no
         * matching host is found by [Authenticator], the [repoUrl] is returned untouched.
         */
        private fun queryAuthenticator(repoUrl: String): String {
            val repoUri = repoUrl.toUri().getOrElse {
                log.warn { "Host cannot be extracted for $repoUrl." }
                return repoUrl
            }

            log.info { "Requesting authenticator for host ${repoUri.host} ..." }

            val creds = Authenticator.getDefault().requestPasswordAuthenticationInstance(
                repoUri.host,
                null,
                0,
                null,
                null,
                null,
                null,
                Authenticator.RequestorType.SERVER
            )
            return creds?.let {
                repoUrl.replaceCredentialsInUri("${creds.userName}:${String(creds.password)}")
            } ?: repoUrl
        }
    }

    /**
     * The qualifier of a scan when delta scans are enabled.
     */
    internal enum class DeltaTag {
        /**
         * Qualifier used when there is no scan and the first one is created.
         */
        ORIGIN,

        /**
         * Qualifier used for all the scans after the first one.
         */
        DELTA
    }

    private val secretKeys = listOf("serverUrl", "apiKey", "user")
    private val namingProvider = config.createNamingProvider()

    // A list of all scans created in an ORT run, to be able to delete them in case of error.
    // The reasoning is that either all these scans are successful, either none is created at all (clean slate).
    // A use case is that an ORT run is created regularly e.g. nightly, and we want to have exactly the same amount
    // of scans for each package.
    private val createdScans = mutableSetOf<String>()

    private val service = config.createService()

    override val version: String = service.version

    override val configuration = ""

    override fun filterOptionsForResult(options: ScannerOptions) =
        options.mapValues { (k, v) ->
            v.takeUnless { k in secretKeys }.orEmpty()
        }

    private suspend fun getProject(projectCode: String): Project? =
        service.getProject(config.user, config.apiKey, projectCode).run {
            when {
                error == null && data != null -> {
                    FossId.log.info { "Project '$projectCode' exists." }
                    data
                }

                error == "Project does not exist" && status == 0 -> {
                    FossId.log.info { "Project '$projectCode' does not exist." }
                    null
                }

                else -> throw IOException("Could not get project. Additional information : $error")
            }
        }

    override suspend fun scanPackages(
        packages: Collection<Package>,
        outputDirectory: File
    ): Map<Package, List<ScanResult>> {
        if (version.isEmpty()) {
            log.warn { "Version from FossId Server cannot be found!" }
        } else {
            log.info { "Version from FossId Server is $version." }
        }

        val (results, duration) = measureTimedValue {
            val results = mutableMapOf<Package, MutableList<ScanResult>>()

            log.info {
                if (config.packageNamespaceFilter.isEmpty()) "No package namespace filter is set."
                else "Package namespace filter is '${config.packageNamespaceFilter}'."
            }
            log.info {
                if (config.packageAuthorsFilter.isEmpty()) "No package authors filter is set."
                else "Package authors filter is '${config.packageAuthorsFilter}'."
            }

            val filteredPackages = packages
                .filter { config.packageNamespaceFilter.isEmpty() || it.id.namespace == config.packageNamespaceFilter }
                .filter { config.packageAuthorsFilter.isEmpty() || config.packageAuthorsFilter in it.authors }
                .onEach {
                    if (it.vcsProcessed.path.isNotEmpty()) {
                        log.warn {
                            "Ignoring package with url ${it.vcsProcessed.url} " +
                                    "with non-null path ${it.vcsProcessed.path}"
                        }

                        val provenance = RepositoryProvenance(it.vcsProcessed, it.vcsProcessed.revision)
                        val summary = createSingleIssueSummary(
                            it.id.toCoordinates(),
                            "This package has been ignored because it contains a non-empty VCS path. " +
                                    "FossID does not support partial checkouts of a Git repository.",
                            Severity.HINT,
                            Instant.now()
                        )

                        val scanResult = ScanResult(provenance, details, summary)
                        results.getOrPut(it) { mutableListOf() } += scanResult
                    }
                }.filter { it.vcsProcessed.path.isEmpty() }

            if (filteredPackages.isEmpty()) {
                log.warn { "There is no package to scan !" }
                return results
            }

            filteredPackages.forEach { pkg ->
                val startTime = Instant.now()

                // TODO: Continue the processing of other packages and add an issue to the scan result.
                require(pkg.vcsProcessed.type == VcsType.GIT) { "FossID only supports Git repositories." }

                val url = pkg.vcsProcessed.url
                val revision = pkg.vcsProcessed.revision.ifEmpty { "HEAD" }
                val projectName = convertGitUrlToProjectName(url)
                val provenance = RepositoryProvenance(pkg.vcsProcessed, pkg.vcsProcessed.revision)

                try {
                    val projectCode = namingProvider.createProjectCode(projectName)

                    if (getProject(projectCode) == null) {
                        log.info { "Creating project '$projectCode' ..." }

                        service.createProject(config.user, config.apiKey, projectCode, projectCode)
                            .checkResponse("create project")
                    }

                    val scans = service.listScansForProject(config.user, config.apiKey, projectCode)
                        .checkResponse("list scans for project").data
                    checkNotNull(scans)

                    val scanCode = if (config.deltaScans) {
                        checkAndCreateDeltaScan(scans, url, revision, projectCode, projectName)
                    } else {
                        checkAndCreateScan(scans, url, revision, projectCode, projectName)
                    }

                    if (config.waitForResult) {
                        val rawResults = getRawResults(scanCode)
                        val resultsSummary = createResultSummary(startTime, provenance, rawResults)

                        results.getOrPut(pkg) { mutableListOf() } += resultsSummary
                    } else {
                        val summary = createSingleIssueSummary(
                            pkg.id.toCoordinates(),
                            "This package has been scanned in asynchronous mode. Scan results are " +
                                    "available on the FossID instance.",
                            Severity.HINT,
                            startTime
                        )

                        val scanResult = ScanResult(provenance, details, summary)
                        results.getOrPut(pkg) { mutableListOf() } += scanResult
                    }
                } catch (e: IllegalStateException) {
                    e.showStackTrace()
                    log.error("Package at url=$url cannot be scanned")

                    val summary = createSingleIssueSummary(
                        pkg.id.toCoordinates(),
                        "This package has failed to be scanned by FossID.",
                        Severity.ERROR,
                        startTime
                    )

                    val scanResult = ScanResult(provenance, details, summary)
                    results.getOrPut(pkg) { mutableListOf() } += scanResult

                    createdScans.forEach {
                        log.warn("Deleting previous scan $it.")
                        deleteScan(it)
                    }
                }
            }

            results
        }

        log.info { "Scan has been performed. Total time was ${duration.inWholeSeconds}s." }

        return results
    }

    private fun createSingleIssueSummary(
        source: String,
        message: String,
        severity: Severity,
        startTime: Instant
    ): ScanSummary {
        val issue = OrtIssue(source = source, message = message, severity = severity)
        return ScanSummary(startTime, Instant.now(), "", sortedSetOf(), sortedSetOf(), listOf(issue))
    }

    private suspend fun List<Scan>.findLatestPendingOrFinishedScan(
        url: String,
        revision: String? = null
    ): Scan? =
        filter {
            // The scans in the server contain the url with the credentials so we have to remove it for the
            // comparison. If we don't, the scans won't be matched if the password changes!
            val urlWithoutCredentials = it.gitRepoUrl?.replaceCredentialsInUri()
            urlWithoutCredentials == url && (revision == null || (it.gitBranch == revision))
        }.sortedByDescending { scan -> scan.id }.find { scan ->
            val scanCode = requireNotNull(scan.code) {
                "FossId returned a null scancode for an existing scan."
            }

            val response = service.checkScanStatus(config.user, config.apiKey, scanCode)
                .checkResponse("check scan status", false)
            when (response.data?.status) {
                ScanStatus.FINISHED -> true
                null, ScanStatus.NOT_STARTED, ScanStatus.INTERRUPTED, ScanStatus.NEW -> false
                ScanStatus.STARTED, ScanStatus.STARTING, ScanStatus.RUNNING, ScanStatus.SCANNING, ScanStatus.AUTO_ID,
                ScanStatus.QUEUED -> {
                    log.warn { "Found previous scan but it is still running." }
                    log.warn { "Ignoring the 'waitForResult' option and waiting ..." }
                    waitScanComplete(scanCode)
                    true
                }
            }
        }

    private suspend fun checkAndCreateScan(
        scans: List<Scan>,
        url: String,
        revision: String,
        projectCode: String,
        projectName: String
    ): String {
        val existingScan = scans.findLatestPendingOrFinishedScan(url, revision)

        val scanCode = if (existingScan == null) {
            log.info { "No scan found for $url and revision $revision. Creating scan ..." }

            val scanCode = namingProvider.createScanCode(projectName)
            val newUrl = if (config.addAuthenticationToUrl) queryAuthenticator(url) else url
            createScan(projectCode, scanCode, newUrl, revision)

            log.info { "Initiating data download ..." }
            service.downloadFromGit(config.user, config.apiKey, scanCode)
                .checkResponse("download data from Git", false)

            scanCode
        } else {
            log.info { "Scan ${existingScan.code} found for $url and revision $revision." }

            requireNotNull(existingScan.code) {
                "FossId returned a null scancode for an existing scan"
            }
        }
        if (config.waitForResult) checkScan(scanCode)
        return scanCode
    }

    private suspend fun checkAndCreateDeltaScan(
        scans: List<Scan>,
        url: String,
        revision: String,
        projectCode: String,
        projectName: String
    ): String {
        // we ignore the revision because we want to do a delta scan
        val existingScan = scans.findLatestPendingOrFinishedScan(url)

        val scanCode = if (existingScan == null) {
            log.info { "No scan found for $url and revision $revision. Creating origin scan ..." }
            namingProvider.createScanCode(projectName, DeltaTag.ORIGIN)
        } else {
            log.info { "Scan found for $url and revision $revision. Creating delta scan ..." }
            namingProvider.createScanCode(projectName, DeltaTag.DELTA)
        }

        val newUrl = if (config.addAuthenticationToUrl) queryAuthenticator(url) else url
        createScan(projectCode, scanCode, newUrl, revision)

        log.info { "Initiating data download ..." }
        service.downloadFromGit(config.user, config.apiKey, scanCode)
            .checkResponse("download data from Git", false)

        if (existingScan == null) {
            checkScan(scanCode)
        } else {
            val existingScancode = requireNotNull(existingScan.code) {
                "FossId returned a null scancode for an existing scan"
            }

            log.info { "Reusing identifications from $existingScancode." }

            // TODO: Change the logic of 'waitForResult' to wait for download results but not for scan results.
            //  Hence we could trigger 'runScan' even when 'waitForResult' is set to false.
            if (!config.waitForResult) {
                log.info { "Ignoring unset 'waitForResult' because delta scans are requested." }
            }

            checkScan(scanCode, *deltaScanRunParameters(existingScancode))
        }

        return scanCode
    }

    /**
     * Create a new scan in the FossID server and return the scan code.
     */
    private suspend fun createScan(
        projectCode: String,
        scanCode: String,
        url: String,
        revision: String
    ): String {
        log.info { "Creating scan $scanCode ..." }

        val response = service.createScan(config.user, config.apiKey, projectCode, scanCode, url, revision)
            .checkResponse("create scan")

        val scanId = response.data?.get("scan_id")

        requireNotNull(scanId) { "Scan could not be created. The response was: ${response.message}." }

        log.info { "Scan has been created with ID $scanId." }
        createdScans.add(scanCode)
        return scanCode
    }

    /**
     * Check the repository has been downloaded and the scan has completed. The latter will be triggered if needed.
     */
    private suspend fun checkScan(scanCode: String, vararg runOptions: Pair<String, String>) {
        waitDownloadComplete(scanCode)

        val response = service.checkScanStatus(config.user, config.apiKey, scanCode)
            .checkResponse("check scan status", false)

        if (response.data?.status in SCAN_STATE_FOR_TRIGGER) {
            log.info { "Triggering scan as it has not yet been started." }

            service.runScan(config.user, config.apiKey, scanCode, *runOptions)
                .checkResponse("trigger scan", false)

            waitScanComplete(scanCode)
        }
    }

    /**
     * Wait for the lambda [waitLoop] to return true, waiting [loopDelay] between each invocation.
     * [waitLoop] should return true if the wait must be interrupted, false otherwise.
     *
     * A [timeout] will be honored and null will be returned if the timeout has been reached.
     */
    private suspend fun wait(timeout: Long, loopDelay: Long, waitLoop: suspend () -> Boolean) =
        withTimeoutOrNull(timeout) {
            while (!waitLoop()) {
                delay(loopDelay)
            }
        }

    /**
     * Wait until the repository of a scan with [scanCode] has been downloaded.
     */
    private suspend fun waitDownloadComplete(scanCode: String) {
        val result = wait(WAIT_INTERVAL_MS * WAIT_REPETITION, WAIT_INTERVAL_MS) {
            FossId.log.info { "Checking download status for scan code '$scanCode'." }

            val response = service.checkDownloadStatus(config.user, config.apiKey, scanCode)
                .checkResponse("check download status")

            if (response.data == DownloadStatus.FINISHED) return@wait true

            // There is a bug with the FossId server version < 20.2: Sometimes the download is complete but it stays in
            // state "NOT FINISHED". Therefore we check the output of the Git fetch to find out whether the download is
            // actually done.
            val message = response.message
            if (message == null || !GIT_FETCH_DONE_REGEX.containsMatchIn(message)) return@wait false

            FossId.log.warn { "The download is not finished but Git Fetch has completed. Carrying on..." }

            return@wait true
        }

        requireNotNull(result) { "Timeout while waiting for the download to complete" }

        log.info { "Data download has been completed." }
    }

    /**
     * Wait until a scan with [scanCode] has completed.
     */
    private suspend fun waitScanComplete(scanCode: String) {
        val result = wait(WAIT_INTERVAL_MS * WAIT_REPETITION, WAIT_INTERVAL_MS) {
            FossId.log.info { "Waiting for scan='$scanCode' to complete." }

            val response = service.checkScanStatus(config.user, config.apiKey, scanCode)
                .checkResponse("check scan status", false)

            response.data?.let {
                if (it.status == ScanStatus.FINISHED) {
                    true
                } else {
                    FossId.log.info {
                        "Scan status for scan code '$scanCode' is '${response.data?.status}'. Waiting ..."
                    }

                    false
                }
            } ?: false
        }

        requireNotNull(result) { "Timeout while waiting for the scan to complete" }

        log.info { "Scan has been completed." }
    }

    /**
     * Delete a scan with [scanCode].
     */
    private suspend fun deleteScan(scanCode: String) {
        val response = service.deleteScan(config.user, config.apiKey, scanCode)
        response.error?.let {
            log.error { "Cannot delete scan $scanCode: $it." }
        }
    }

    /**
     * Get the different kind of results from the scan with [scanCode]
     */
    private suspend fun getRawResults(scanCode: String): RawResults {
        val identifiedFiles = service.listIdentifiedFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list identified files")
            .data!!
        log.info { "${identifiedFiles.size} identified files have been returned for scan code $scanCode." }

        val markedAsIdentifiedFiles = service.listMarkedAsIdentifiedFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list marked as identified files")
            .data!!
        log.info {
            "${markedAsIdentifiedFiles.size} marked as identified files have been returned for scan code $scanCode."
        }

        // The "match_type=ignore" info is already in the ScanResult, but here we also get the ignore reason.
        val listIgnoredFiles = service.listIgnoredFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list ignored files")
            .data!!

        val pendingFiles = service.listPendingFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list pending files")
            .data!!
        log.info {
            "${pendingFiles.size} pending files have been returned for scan code $scanCode."
        }

        return RawResults(identifiedFiles, markedAsIdentifiedFiles, listIgnoredFiles, pendingFiles)
    }

    /**
     * Construct the [ScanSummary] for this FossId scan.
     */
    private fun createResultSummary(startTime: Instant, provenance: Provenance, rawResults: RawResults): ScanResult {
        val associate = rawResults.listIgnoredFiles.associateBy { it.path }

        val (licenseFindings, copyrightFindings) = rawResults.markedAsIdentifiedFiles.ifEmpty {
            rawResults.identifiedFiles
        }.mapSummary(associate)

        val summary = ScanSummary(
            startTime = startTime,
            endTime = Instant.now(),
            packageVerificationCode = "",
            licenseFindings = licenseFindings.toSortedSet(),
            copyrightFindings = copyrightFindings.toSortedSet(),
            // TODO: Maybe get issues from FossId (see has_failed_scan_files, get_failed_files and maybe get_scan_log).
            issues = rawResults.listPendingFiles.map {
                OrtIssue(source = it, message = "pending", severity = Severity.HINT)
            }
        )

        return ScanResult(provenance, details, summary)
    }
}
