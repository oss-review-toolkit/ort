/*
 * Copyright (C) 2021-2022 Bosch.IO GmbH
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

import java.io.IOException
import java.net.Authenticator
import java.time.Instant

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.checkDownloadStatus
import org.ossreviewtoolkit.clients.fossid.checkResponse
import org.ossreviewtoolkit.clients.fossid.createIgnoreRule
import org.ossreviewtoolkit.clients.fossid.createProject
import org.ossreviewtoolkit.clients.fossid.createScan
import org.ossreviewtoolkit.clients.fossid.deleteScan
import org.ossreviewtoolkit.clients.fossid.downloadFromGit
import org.ossreviewtoolkit.clients.fossid.getProject
import org.ossreviewtoolkit.clients.fossid.listIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listIgnoreRules
import org.ossreviewtoolkit.clients.fossid.listIgnoredFiles
import org.ossreviewtoolkit.clients.fossid.listMarkedAsIdentifiedFiles
import org.ossreviewtoolkit.clients.fossid.listPendingFiles
import org.ossreviewtoolkit.clients.fossid.listScansForProject
import org.ossreviewtoolkit.clients.fossid.model.Project
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleScope
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType
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
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.Options
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.Scanner
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.scanner.experimental.AbstractScannerWrapperFactory
import org.ossreviewtoolkit.scanner.experimental.PackageScannerWrapper
import org.ossreviewtoolkit.scanner.experimental.ScanContext
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication
import org.ossreviewtoolkit.utils.ort.showStackTrace

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
) : Scanner(name, scannerConfig, downloaderConfig), PackageScannerWrapper {
    class FossIdFactory : AbstractScannerWrapperFactory<FossId>("FossId") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            FossId(scannerName, scannerConfig, downloaderConfig, FossIdConfig.create(scannerConfig))
    }

    class Factory : AbstractScannerFactory<FossId>("FossId") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            FossId(scannerName, scannerConfig, downloaderConfig, FossIdConfig.create(scannerConfig))
    }

    companion object {
        @JvmStatic
        private val PROJECT_NAME_REGEX = Regex("""^.*/([\w.\-]+?)(?:\.git)?$""")

        @JvmStatic
        private val GIT_FETCH_DONE_REGEX = Regex("-> FETCH_HEAD(?: Already up to date.)*$")

        @JvmStatic
        private val WAIT_DELAY = 10.seconds

        @JvmStatic
        internal val SCAN_CODE_KEY = "scancode"

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

            log.info { "Found project name '$projectName' in URL '$gitRepoUrl'." }

            return projectName
        }

        /**
         * Generate a list of pairs to be passed as parameters when starting a new delta scan for [existingScanCode].
         */
        internal fun deltaScanRunParameters(existingScanCode: String): Array<Pair<String, String>> =
            arrayOf(
                "reuse_identification" to "1",
                "identification_reuse_type" to "specific_scan",
                "specific_code" to existingScanCode
            )

        /**
         * This function fetches credentials for [repoUrl] and insert them between the URL scheme and the host. If no
         * matching host is found by [Authenticator], the [repoUrl] is returned untouched.
         */
        private fun queryAuthenticator(repoUrl: String): String {
            val repoUri = repoUrl.toUri().getOrElse {
                log.warn { "The repository URL '$repoUrl' is not valid." }
                return repoUrl
            }

            log.info { "Requesting authentication for host ${repoUri.host} ..." }

            val creds = requestPasswordAuthentication(repoUri)
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

    private val service = FossIdRestService.createService(config.serverUrl)

    override val criteria: ScannerCriteria? = null
    override val name: String = "FossId"
    override val version: String = service.version

    override val configuration = ""

    override fun filterSecretOptions(options: Options) =
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
        packages: Set<Package>,
        labels: Map<String, String>
    ): Map<Package, List<ScanResult>> {
        val (results, duration) = measureTimedValue {
            val results = mutableMapOf<Package, MutableList<ScanResult>>()

            fun addPackageWithSingleIssue(pkg: Package, issue: OrtIssue, provenance: Provenance) {
                val time = Instant.now()
                val summary = ScanSummary(time, time, "", sortedSetOf(), sortedSetOf(), listOf(issue))
                val scanResult = ScanResult(provenance, details, summary)
                results.getOrPut(pkg) { mutableListOf() } += scanResult
            }

            val filteredPackages = packages
                .partition { it.vcsProcessed.type == VcsType.GIT }
                .let { (packagesInsideGit, packagesOutsideGit) ->
                    packagesOutsideGit.forEach {
                        val issue = createAndLogIssue(
                            source = scannerName,
                            message = "Package '${it.id.toCoordinates()}' uses VCS type '${it.vcsProcessed.type}', " +
                                    "but only ${VcsType.GIT} is supported.",
                            severity = Severity.WARNING
                        )
                        addPackageWithSingleIssue(it, issue, UnknownProvenance)
                    }

                    packagesInsideGit
                }
                .partition { it.vcsProcessed.revision.isEmpty() }
                .let { (packagesWithoutRevisions, packagesWithRevisions) ->
                    packagesWithoutRevisions.forEach {
                        val issue = createAndLogIssue(
                            source = scannerName,
                            message = "Package '${it.id.toCoordinates()}' has an empty VCS revision and cannot be " +
                                    "scanned.",
                            severity = Severity.WARNING
                        )
                        addPackageWithSingleIssue(it, issue, UnknownProvenance)
                    }

                    packagesWithRevisions
                }
                .partition { it.vcsProcessed.path.isEmpty() }
                .let { (packagesWithoutPaths, packagesWithPaths) ->
                    packagesWithPaths.forEach {
                        val issue = createAndLogIssue(
                            source = scannerName,
                            message = "Ignoring package '${it.id.toCoordinates()}' from ${it.vcsProcessed.url} as it " +
                                    "has path '${it.vcsProcessed.path}' set and scanning cannot be limited to paths.",
                            severity = Severity.WARNING
                        )
                        val provenance = RepositoryProvenance(it.vcsProcessed, it.vcsProcessed.revision)
                        addPackageWithSingleIssue(it, issue, provenance)
                    }

                    packagesWithoutPaths
                }

            if (filteredPackages.isEmpty()) {
                log.warn { "There is no package to scan." }
                return results
            }

            filteredPackages.forEach { pkg ->
                val startTime = Instant.now()
                val url = pkg.vcsProcessed.url
                val revision = pkg.vcsProcessed.revision
                val projectName = convertGitUrlToProjectName(url)
                val provenance = RepositoryProvenance(pkg.vcsProcessed, revision)

                try {
                    val projectCode = namingProvider.createProjectCode(projectName)

                    if (getProject(projectCode) == null) {
                        log.info { "Creating project '$projectCode'..." }

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
                        val resultsSummary = createResultSummary(startTime, provenance, rawResults, scanCode)

                        results.getOrPut(pkg) { mutableListOf() } += resultsSummary
                    } else {
                        val issue = createAndLogIssue(
                            source = scannerName,
                            message = "Package '${pkg.id.toCoordinates()}' has been scanned in asynchronous mode. " +
                                    "Scan results need to be inspected on the server instance.",
                            severity = Severity.HINT
                        )
                        val summary = ScanSummary(
                            startTime, Instant.now(), "", sortedSetOf(), sortedSetOf(), listOf(issue)
                        )

                        val scanResult = ScanResult(provenance, details, summary, mapOf(SCAN_CODE_KEY to scanCode))
                        results.getOrPut(pkg) { mutableListOf() } += scanResult
                    }
                } catch (e: IllegalStateException) {
                    e.showStackTrace()

                    val issue = createAndLogIssue(
                        source = scannerName,
                        message = "Failed to scan package '${pkg.id.toCoordinates()}' from $url."
                    )
                    val summary = ScanSummary(startTime, Instant.now(), "", sortedSetOf(), sortedSetOf(), listOf(issue))

                    val scanResult = ScanResult(provenance, details, summary)
                    results.getOrPut(pkg) { mutableListOf() } += scanResult

                    if (!config.keepFailedScans) {
                        createdScans.forEach { code ->
                            log.warn { "Deleting scan '$code' during exception cleanup." }
                            deleteScan(code)
                        }
                    }
                }
            }

            results
        }

        log.info { "Scan has been performed. Total time was $duration." }

        return results
    }

    /**
     * Find the latest [Scan] in this list with a finished state. If necessary, wait for a scan to finish. Note that
     * this function expects that [recentScansForRepository] has been applied to this list for the current
     * repository.
     */
    private suspend fun List<Scan>.findLatestPendingOrFinishedScan(): Scan? =
        find { scan ->
            val scanCode = requireNotNull(scan.code) {
                "The code for an existing scan must not be null."
            }

            val response = service.checkScanStatus(config.user, config.apiKey, scanCode)
                .checkResponse("check scan status", false)
            when (response.data?.status) {
                ScanStatus.FINISHED -> true

                null, ScanStatus.NOT_STARTED, ScanStatus.INTERRUPTED, ScanStatus.NEW, ScanStatus.FAILED -> false

                ScanStatus.STARTED, ScanStatus.STARTING, ScanStatus.RUNNING, ScanStatus.SCANNING, ScanStatus.AUTO_ID,

                ScanStatus.QUEUED -> {
                    FossId.log.warn {
                        "Found a previous scan which is still running. Will ignore the 'waitForResult' option and " +
                                "wait..."
                    }
                    waitScanComplete(scanCode)
                    true
                }
            }
        }

    /**
     * Filter this list of [Scan]s for the repository defined by [url] and an optional [revision] and sort it by
     * scan ID, so that the most recent scan comes first and the oldest scan comes last.
     */
    private fun List<Scan>.recentScansForRepository(
        url: String,
        revision: String? = null
    ): List<Scan> = filter {
        val isArchived = it.isArchived ?: false
        // The scans in the server contain the url with the credentials, so we have to remove it for the
        // comparison. If we don't, the scans won't be matched if the password changes!
        val urlWithoutCredentials = it.gitRepoUrl?.replaceCredentialsInUri()
        !isArchived && urlWithoutCredentials == url && (revision == null || it.gitBranch == revision)
    }.sortedByDescending { scan -> scan.id }

    private suspend fun checkAndCreateScan(
        scans: List<Scan>,
        url: String,
        revision: String,
        projectCode: String,
        projectName: String
    ): String {
        val existingScan = scans.recentScansForRepository(url, revision).findLatestPendingOrFinishedScan()

        val scanCode = if (existingScan == null) {
            log.info { "No scan found for $url and revision $revision. Creating scan..." }

            val scanCode = namingProvider.createScanCode(projectName)
            val newUrl = if (config.addAuthenticationToUrl) queryAuthenticator(url) else url
            createScan(projectCode, scanCode, newUrl, revision)

            log.info { "Initiating the download..." }
            service.downloadFromGit(config.user, config.apiKey, scanCode)
                .checkResponse("download data from Git", false)

            scanCode
        } else {
            log.info { "Scan '${existingScan.code}' found for $url and revision $revision." }

            requireNotNull(existingScan.code) {
                "The code for an existing scan must not be null."
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
        val urlWithoutCredentials = url.replaceCredentialsInUri()
        if (urlWithoutCredentials != url) {
            log.warn { "The URL should not contain credentials as its interaction with delta scans is unpredictable." }
        }

        // we ignore the revision because we want to do a delta scan
        val recentScans = scans.recentScansForRepository(urlWithoutCredentials)

        log.info { "Found ${recentScans.size} scans." }

        val existingScan = recentScans.findLatestPendingOrFinishedScan()

        val scanCode = if (existingScan == null) {
            log.info { "No scan found for $urlWithoutCredentials and revision $revision. Creating origin scan..." }
            namingProvider.createScanCode(projectName, DeltaTag.ORIGIN)
        } else {
            log.info { "Scan found for $urlWithoutCredentials and revision $revision. Creating delta scan..." }
            namingProvider.createScanCode(projectName, DeltaTag.DELTA)
        }

        val newUrl = if (config.addAuthenticationToUrl) {
            queryAuthenticator(urlWithoutCredentials)
        } else {
            urlWithoutCredentials
        }

        createScan(projectCode, scanCode, newUrl, revision)

        log.info { "Initiating the download..." }
        service.downloadFromGit(config.user, config.apiKey, scanCode)
            .checkResponse("download data from Git", false)

        if (existingScan == null) {
            if (config.waitForResult) checkScan(scanCode)
        } else {
            val existingScanCode = requireNotNull(existingScan.code) {
                "The code for an existing scan must not be null."
            }

            log.info { "Loading ignore rules from '$existingScanCode'." }

            val ignoreRules = service.listIgnoreRules(config.user, config.apiKey, existingScanCode)
                .checkResponse("list ignore rules")
            ignoreRules.data?.let { rules ->
                log.info { "${rules.size} ignore rule(s) have been found." }

                // When a scan is created with the optional property 'git_repo_url', the server automatically creates
                // an 'ignore rule' to exclude the '.git' directory.
                // Therefore, this rule will be created automatically and does not need to be carried from the old scan.
                rules.filterNot { it.type == RuleType.DIRECTORY && it.value == ".git" }.forEach {
                    service.createIgnoreRule(config.user, config.apiKey, scanCode, it.type, it.value, RuleScope.SCAN)
                        .checkResponse("create ignore rules", false)
                    log.info {
                        "Ignore rule of type '${it.type}' and value '${it.value}' has been carried to the new scan."
                    }
                }
            }

            log.info { "Reusing identifications from scan '$existingScanCode'." }

            // TODO: Change the logic of 'waitForResult' to wait for download results but not for scan results.
            //  Hence we could trigger 'runScan' even when 'waitForResult' is set to false.
            if (!config.waitForResult) {
                log.info { "Ignoring unset 'waitForResult' because delta scans are requested." }
            }

            checkScan(scanCode, *deltaScanRunParameters(existingScanCode))

            enforceDeltaScanLimit(recentScans)
        }

        return scanCode
    }

    /**
     * Make sure that only the configured number of delta scans exists for the current package. Based on the list of
     * [existingScans], delete older scans until the maximum number of delta scans is reached.
     */
    private suspend fun enforceDeltaScanLimit(existingScans: List<Scan>) {
        log.info { "Will retain up to ${config.deltaScanLimit} delta scans." }

        // The current scan needs to be counted as well, in addition to the already existing scans.
        if (existingScans.size + 1 > config.deltaScanLimit) {
            log.info { "Deleting ${existingScans.size + 1 - config.deltaScanLimit} older scans." }
        }

        // Drop the most recent scans to keep in order to iterate over the remaining ones to delete them.
        existingScans.drop(config.deltaScanLimit - 1)
            .forEach { scan ->
                scan.code?.let { code ->
                    log.info { "Deleting scan '$code' to enforce the maximum number of delta scans." }
                    deleteScan(code)
                }
            }
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
        log.info { "Creating scan '$scanCode'..." }

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

        check(response.data?.status != ScanStatus.FAILED) { "Triggered scan has failed." }

        if (response.data?.status in SCAN_STATE_FOR_TRIGGER) {
            log.info { "Triggering scan as it has not yet been started." }

            val scanResult = service.runScan(config.user, config.apiKey, scanCode, *runOptions)

            // Scans that were added to the queue are interpreted as an error by FossID before version 2021.2.
            // For older versions, `waitScanComplete()` is able to deal with queued scans. Therefore, not checking the
            // response of queued scans.
            if (version >= "2021.2" || scanResult.error != "Scan was added to queue.") {
                scanResult.checkResponse("trigger scan", false)
            }

            waitScanComplete(scanCode)
        }
    }

    /**
     * Loop for the lambda [condition] to return true, with the given [delay] between loop iterations. If the [timeout]
     * has been reached, return in any case.
     */
    private suspend fun wait(timeout: Duration, delay: Duration, condition: suspend () -> Boolean) =
        withTimeoutOrNull(timeout) {
            while (!condition()) {
                delay(delay)
            }
        }

    /**
     * Wait until the repository of a scan with [scanCode] has been downloaded.
     */
    private suspend fun waitDownloadComplete(scanCode: String) {
        val result = wait(config.timeout.minutes, WAIT_DELAY) {
            FossId.log.info { "Checking download status for scan '$scanCode'." }

            val response = service.checkDownloadStatus(config.user, config.apiKey, scanCode)
                .checkResponse("check download status")

            when (response.data) {
                DownloadStatus.FINISHED -> return@wait true

                DownloadStatus.FAILED -> error("Could not download scan: ${response.message}.")

                else -> {
                    // There is a bug with the FossID server version < 20.2: Sometimes the download is complete, but it
                    // stays in state "NOT FINISHED". Therefore, we check the output of the Git fetch to find out
                    // whether the download is actually done.
                    val message = response.message
                    if (message == null || !GIT_FETCH_DONE_REGEX.containsMatchIn(message)) return@wait false

                    FossId.log.warn { "The download is not finished but Git Fetch has completed. Carrying on..." }

                    return@wait true
                }
            }
        }

        requireNotNull(result) { "Timeout while waiting for the download to complete" }

        log.info { "Data download has been completed." }
    }

    /**
     * Wait until a scan with [scanCode] has completed.
     */
    private suspend fun waitScanComplete(scanCode: String) {
        val result = wait(config.timeout.minutes, WAIT_DELAY) {
            FossId.log.info { "Waiting for scan '$scanCode' to complete." }

            val response = service.checkScanStatus(config.user, config.apiKey, scanCode)
                .checkResponse("check scan status", false)

            when (response.data?.status) {
                ScanStatus.FINISHED -> true
                ScanStatus.FAILED -> error("Scan waited for has failed.")
                null -> false
                else -> {
                    FossId.log.info {
                        "Scan status for scan '$scanCode' is '${response.data?.status}'. Waiting..."
                    }

                    false
                }
            }
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
            log.error { "Cannot delete scan '$scanCode': $it." }
        }
    }

    /**
     * Get the different kind of results from the scan with [scanCode]
     */
    private suspend fun getRawResults(scanCode: String): RawResults {
        val identifiedFiles = service.listIdentifiedFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list identified files")
            .data!!
        log.info { "${identifiedFiles.size} identified files have been returned for scan '$scanCode'." }

        val markedAsIdentifiedFiles = service.listMarkedAsIdentifiedFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list marked as identified files")
            .data!!
        log.info {
            "${markedAsIdentifiedFiles.size} marked as identified files have been returned for scan '$scanCode'."
        }

        // The "match_type=ignore" info is already in the ScanResult, but here we also get the ignore reason.
        val listIgnoredFiles = service.listIgnoredFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list ignored files")
            .data!!

        val pendingFiles = service.listPendingFiles(config.user, config.apiKey, scanCode)
            .checkResponse("list pending files")
            .data!!
        log.info {
            "${pendingFiles.size} pending files have been returned for scan '$scanCode'."
        }

        return RawResults(identifiedFiles, markedAsIdentifiedFiles, listIgnoredFiles, pendingFiles)
    }

    /**
     * Construct the [ScanSummary] for this FossID scan.
     */
    private fun createResultSummary(
        startTime: Instant,
        provenance: Provenance,
        rawResults: RawResults,
        scanCode: String
    ): ScanResult {
        // TODO: Maybe get issues from FossID (see has_failed_scan_files, get_failed_files and maybe get_scan_log).
        val issues = rawResults.listPendingFiles.mapTo(mutableListOf()) {
            OrtIssue(source = scannerName, message = "Pending identification for '$it'.", severity = Severity.HINT)
        }

        val ignoredFiles = rawResults.listIgnoredFiles.associateBy { it.path }

        val (licenseFindings, copyrightFindings) = rawResults.markedAsIdentifiedFiles.ifEmpty {
            rawResults.identifiedFiles
        }.mapSummary(ignoredFiles, issues, scannerConfig.detectedLicenseMapping)

        val summary = ScanSummary(
            startTime = startTime,
            endTime = Instant.now(),
            packageVerificationCode = "",
            licenseFindings = licenseFindings.toSortedSet(),
            copyrightFindings = copyrightFindings.toSortedSet(),
            issues = issues
        )

        return ScanResult(provenance, details, summary, mapOf(SCAN_CODE_KEY to scanCode))
    }

    override fun scanPackage(pkg: Package, context: ScanContext): ScanResult =
        runBlocking {
            scanPackages(setOf(pkg), context.labels).getValue(pkg).first()
        }
}
