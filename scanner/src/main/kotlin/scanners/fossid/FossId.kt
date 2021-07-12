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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.checkDownloadStatus
import org.ossreviewtoolkit.clients.fossid.checkResponse
import org.ossreviewtoolkit.clients.fossid.checkScanStatus
import org.ossreviewtoolkit.clients.fossid.createProject
import org.ossreviewtoolkit.clients.fossid.createScan
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
import org.ossreviewtoolkit.clients.fossid.model.status.ScanState
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
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.toUri

import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

/**
 * A wrapper for [FossID](https://fossid.com/).
 *
 * This scanner can be configured in [ScannerConfiguration.options] using the key "FossId". It offers the following
 * configuration options:
 *
 * * **"serverUrl":** The URL of the FossID server.
 * * **"user":** The user to connect to the FossID server.
 * * **"apiKey":** The API key of the user which connects to the FossID server.
 * * **"packageNamespaceFilter":** If this optional filter is set, only packages having an identifier in given namespace
 * will be scanned.
 * * **"packageAuthorsFilter":** If this optional filter is set, only packages from a given author will be scanned.
 * * **"addAuthenticationToUrl":** When set, ORT will add credentials from its Authenticator to the URLs sent to FossID.
 * * **"waitForResult":** When set to false, ORT doesn't wait for repositories to be downloaded nor scans to be
 * completed. As a consequence, scan results won't be available in ORT result.
 * * **"deltaScans":** When set, ORT will create delta scans. When only changes in a repository need to be scanned,
 * delta scans reuse the identifications of latest scan on this repository to reduce the amount of findings. If
 * deltaScans is set and no scan exist yet, an initial scan called "origin" scan will be created.
 *
 * Naming conventions options. If they are not set, default naming convention are used.
 * * **"namingProjectPattern":** A pattern for project names when projects are created on the FossID instance. Contains
 * variables prefixed by "$" e.g. "$Var1_$Var2". Variables are also passed as options and are prefixed by
 * [NAMING_CONVENTION_VARIABLE_PREFIX] e.g. namingVariableVar1 = "foo".
 * * **"namingScanPattern":** A pattern for scan names when scans are created on the FossID instance.
 */
class FossId(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : RemoteScanner(name, scannerConfig, downloaderConfig) {
    class Factory : AbstractScannerFactory<FossId>("FossId") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            FossId(scannerName, scannerConfig, downloaderConfig)
    }

    companion object {
        @JvmStatic
        private val PROJECT_NAME_REGEX = Regex("""^.*\/([\w.\-]+)(?:\.git)?$""")

        @JvmStatic
        private val GIT_FETCH_DONE_REGEX = Regex("-> FETCH_HEAD(?: Already up to date.)*$")

        @JvmStatic
        private val WAIT_INTERVAL_MS = 10000L

        @JvmStatic
        private val WAIT_REPETITION = 50

        /**
         * The scanner options beginning with this prefix will be used to parametrize project and scan names.
         */
        private const val NAMING_CONVENTION_VARIABLE_PREFIX = "namingVariable"

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

    private val serverUrl: String
    private val apiKey: String
    private val user: String
    private val waitForResult: Boolean
    private val secretKeys = listOf("serverUrl", "apiKey", "user")
    private val namingProvider: FossIdNamingProvider

    private val service: FossIdRestService
    private val packageNamespaceFilter: String
    private val packageAuthorsFilter: String
    private val addAuthenticationToUrl: Boolean
    private val deltaScans: Boolean

    override val version: String

    override val configuration = ""

    init {
        val fossIdScannerOptions = scannerConfig.options?.get("FossId")

        requireNotNull(fossIdScannerOptions) { "No FossId Scanner configuration found." }

        serverUrl = fossIdScannerOptions["serverUrl"]
            ?: throw IllegalArgumentException("No FossId server URL configuration found.")
        apiKey = fossIdScannerOptions["apiKey"]
            ?: throw IllegalArgumentException("No FossId API Key configuration found.")
        user = fossIdScannerOptions["user"]
            ?: throw IllegalArgumentException("No FossId User configuration found.")
        packageNamespaceFilter = fossIdScannerOptions["packageNamespaceFilter"].orEmpty()
        packageAuthorsFilter = fossIdScannerOptions["packageAuthorsFilter"].orEmpty()
        addAuthenticationToUrl = fossIdScannerOptions["addAuthenticationToUrl"].toBoolean()
        waitForResult = fossIdScannerOptions["waitForResult"]?.toBooleanStrictOrNull() ?: true
        deltaScans = fossIdScannerOptions["deltaScans"].toBoolean()

        log.info { "waitForResult parameter is set to '$waitForResult'" }

        val namingProjectPattern = fossIdScannerOptions["namingProjectPattern"]?.apply {
            FossId.log.info { "Naming pattern for projects is $this." }
        }
        val namingScanPattern = fossIdScannerOptions["namingScanPattern"]?.apply {
            FossId.log.info { "Naming pattern for scans is $this." }
        }

        val namingConventionVariables = fossIdScannerOptions
            .filterKeys { it.startsWith(NAMING_CONVENTION_VARIABLE_PREFIX) }
            .mapKeys { it.key.substringAfter(NAMING_CONVENTION_VARIABLE_PREFIX) }

        namingProvider = FossIdNamingProvider(namingProjectPattern, namingScanPattern, namingConventionVariables)

        val client = OkHttpClientHelper.buildClient()

        log.info { "FossID server URL is $serverUrl." }

        val retrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(serverUrl)
            .addConverterFactory(JacksonConverterFactory.create(FossIdRestService.JSON_MAPPER))
            .build()

        service = retrofit.create(FossIdRestService::class.java)

        version = runBlocking {
            parseVersion().orEmpty()
        }
    }

    override fun filterOptionsForResult(options: ScannerOptions) =
        options.mapValues { (k, v) ->
            v.takeUnless { k in secretKeys }.orEmpty()
        }

    /**
     * Extract the version version from the login page.
     * Example: &nbsp;&nbsp;&nbsp;cli.  3.1.16 (build 5634934d, RELEASE)
     */
    private suspend fun parseVersion(): String? {
        // TODO: replace with an API call when FossID provides a function (starting at version 21.2).
        val regex = Regex("^.*&nbsp;(cli. *[0-9. ]+\\(build[\\w, ]+\\)).*$")

        val response = service.getLoginPage()

        response.charStream().buffered().useLines { lines ->
            lines.forEach { line ->
                val matcher = regex.matchEntire(line)
                if (matcher != null && matcher.groupValues.size == 2) {
                    val version = matcher.groupValues[1]
                    FossId.log.info { "Version from FossId Server is $version." }
                    return version
                }
            }
        }
        log.warn { "Version from FossId Server cannot be found!" }
        return null
    }

    private suspend fun getProject(projectCode: String): Project? =
        service.getProject(user, apiKey, projectCode).run {
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
        val (results, duration) = measureTimedValue {
            val results = mutableMapOf<Package, MutableList<ScanResult>>()

            log.info {
                if (packageNamespaceFilter.isEmpty()) "No package namespace filter is set."
                else "Package namespace filter is '$packageNamespaceFilter'."
            }
            log.info {
                if (packageAuthorsFilter.isEmpty()) "No package authors filter is set."
                else "Package authors filter is '$packageAuthorsFilter'."
            }

            val filteredPackages = packages
                .filter { packageNamespaceFilter.isEmpty() || it.id.namespace == packageNamespaceFilter }
                .filter { packageAuthorsFilter.isEmpty() || packageAuthorsFilter in it.authors }

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
                val projectCode = namingProvider.createProjectCode(projectName)

                if (getProject(projectCode) == null) {
                    log.info { "Creating project '$projectCode' ..." }

                    service.createProject(user, apiKey, projectCode, projectCode)
                        .checkResponse("create project")
                }

                val scans = service.listScansForProject(user, apiKey, projectCode)
                    .checkResponse("list scans for project").data
                checkNotNull(scans)

                val scanCode = if (deltaScans) {
                    checkAndCreateDeltaScan(scans, revision, url, projectCode, projectName)
                } else {
                    checkAndCreateScan(scans, revision, url, projectCode, projectName)
                }

                val provenance = RepositoryProvenance(pkg.vcsProcessed, pkg.vcsProcessed.revision)

                if (waitForResult) {
                    val rawResults = getRawResults(scanCode)
                    val resultsSummary = createResultSummary(startTime, provenance, rawResults)

                    results.getOrPut(pkg) { mutableListOf() } += resultsSummary
                } else {
                    val issue = OrtIssue(
                        source = pkg.id.toCoordinates(),
                        message = "This package has been scanned in asynchronous mode. Scan results are available " +
                                "on the FossID instance.",
                        severity = Severity.HINT
                    )
                    val summary = ScanSummary(
                        startTime = startTime,
                        endTime = Instant.now(),
                        packageVerificationCode = "",
                        licenseFindings = sortedSetOf(),
                        copyrightFindings = sortedSetOf(),
                        issues = listOf(
                            issue
                        )
                    )

                    val scanResult = ScanResult(provenance, details, summary)
                    results.getOrPut(pkg) { mutableListOf() } += scanResult
                }
            }

            results
        }

        log.info { "Scan has been performed. Total time was ${duration.inWholeSeconds}s." }

        return results
    }

    private suspend fun List<Scan>.findLatestPendingOrFinishedScan(url: String, revision: String? = null): Scan? =
        filter {
            // The scans in the server contain the url with the credentials so we have to remove it for the
            // comparison. If we don't, the scans won't be matched if the password changes!
            val urlWithoutCredentials = it.gitRepoUrl?.replaceCredentialsInUri()
            urlWithoutCredentials == url && (revision == null || (it.gitBranch == revision))
        }.sortedByDescending { scan -> scan.id }.find { scan ->
            val scanCode = requireNotNull(scan.code) {
                "FossId returned a null scancode for an existing scan."
            }

            val response = service.checkScanStatus(user, apiKey, scanCode)
                .checkResponse("check scan status", false)
            when (response.data?.state) {
                ScanState.FINISHED -> true
                null, ScanState.NOT_STARTED, ScanState.INTERRUPTED -> false
                ScanState.STARTED, ScanState.SCANNING, ScanState.AUTO_ID, ScanState.QUEUED -> {
                    log.warn { "Found previous scan but it is still running." }
                    log.warn { "Ignoring the 'waitForResult' option and waiting ..." }
                    waitScanComplete(scanCode)
                    true
                }
            }
        }

    private suspend fun checkAndCreateScan(
        scans: List<Scan>,
        revision: String,
        url: String,
        projectCode: String,
        projectName: String
    ): String {
        val existingScan = scans.findLatestPendingOrFinishedScan(url, revision)

        val scanCode = if (existingScan == null) {
            log.info { "No scan found for $url and revision $revision. Creating scan ..." }

            val scanCode = namingProvider.createScanCode(projectName)
            val newUrl = if (addAuthenticationToUrl) queryAuthenticator(url) else url
            createScan(projectCode, scanCode, newUrl, revision)

            log.info { "Initiating data download ..." }
            service.downloadFromGit(user, apiKey, scanCode)
                .checkResponse("download data from Git", false)

            scanCode
        } else {
            log.info { "Scan ${existingScan.code} found for $url and revision $revision." }

            requireNotNull(existingScan.code) {
                "FossId returned a null scancode for an existing scan"
            }
        }
        if (waitForResult) checkScan(scanCode)
        return scanCode
    }

    private suspend fun checkAndCreateDeltaScan(
        scans: List<Scan>,
        revision: String,
        url: String,
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

        val newUrl = if (addAuthenticationToUrl) queryAuthenticator(url) else url
        createScan(projectCode, scanCode, newUrl, revision)

        log.info { "Initiating data download ..." }
        service.downloadFromGit(user, apiKey, scanCode)
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
            if (!waitForResult) {
                log.info { "Ignoring unset 'waitForResult' because delta scans are requested." }
            }

            checkScan(
                scanCode,
                "reuse_identification" to "1",
                "identification_reuse_type" to "specific_scan",
                "specific_code" to existingScancode
            )
        }

        return scanCode
    }

    /**
     * Create a new scan in the FossID server and return the scan code.
     */
    private suspend fun createScan(projectCode: String, scanCode: String, url: String, revision: String): String {
        log.info { "Creating scan $scanCode ..." }

        val response = service.createScan(user, apiKey, projectCode, scanCode, url, revision)
            .checkResponse("create scan")

        val scanId = response.data?.get("scan_id")

        requireNotNull(scanId) { "Scan could not be created. The response was: ${response.message}." }

        log.info { "Scan has been created with ID $scanId." }

        return scanCode
    }

    /**
     * Check the repository has been downloaded and the scan has completed. The latter will be triggered if needed.
     */
    private suspend fun checkScan(scanCode: String, vararg runOptions: Pair<String, String>) {
        waitDownloadComplete(scanCode)

        val response = service.checkScanStatus(user, apiKey, scanCode)
            .checkResponse("check scan status", false)

        if (response.data?.state == ScanState.NOT_STARTED) {
            log.info { "Triggering scan as it has not yet been started." }

            service.runScan(user, apiKey, scanCode, *runOptions)
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

            val response = service.checkDownloadStatus(user, apiKey, scanCode)
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

            val response = service.checkScanStatus(user, apiKey, scanCode)
                .checkResponse("check scan status", false)

            response.data?.let {
                if (it.state == ScanState.FINISHED) {
                    FossId.log.info { "Scan finished with response: ${response.data?.comment}." }
                    true
                } else {
                    FossId.log.info {
                        "Scan status for scan code '$scanCode' is '${response.data?.state}'. Waiting ..."
                    }

                    false
                }
            } ?: false
        }

        requireNotNull(result) { "Timeout while waiting for the scan to complete" }

        log.info { "Scan has been completed." }
    }

    /**
     * Get the different kind of results from the scan with [scanCode]
     */
    private suspend fun getRawResults(scanCode: String): RawResults {
        val identifiedFiles = service.listIdentifiedFiles(user, apiKey, scanCode)
            .checkResponse("list identified files")
            .data!!
        log.info { "${identifiedFiles.size} identified files have been returned for scan code $scanCode." }

        val markedAsIdentifiedFiles = service.listMarkedAsIdentifiedFiles(user, apiKey, scanCode)
            .checkResponse("list marked as identified files")
            .data!!
        log.info {
            "${markedAsIdentifiedFiles.size} marked as identified files have been returned for scan code $scanCode."
        }

        // The "match_type=ignore" info is already in the ScanResult, but here we also get the ignore reason.
        val listIgnoredFiles = service.listIgnoredFiles(user, apiKey, scanCode)
            .checkResponse("list ignored files")
            .data!!

        val pendingFiles = service.listPendingFiles(user, apiKey, scanCode)
            .checkResponse("list pending files")
            .data!!

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
