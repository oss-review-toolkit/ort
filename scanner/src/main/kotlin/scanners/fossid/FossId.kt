/*
 * Copyright (C) 2021 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import kotlin.time.measureTimedValue

import kotlinx.coroutines.delay
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
import org.ossreviewtoolkit.clients.fossid.model.Project
import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanState
import org.ossreviewtoolkit.clients.fossid.runScan
import org.ossreviewtoolkit.clients.fossid.toList
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.RemoteScanner
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.log

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
        private val PROJECT_NAME_REGEX = Regex("""^.*\/([\w\-]+)(?:\.git)?$""")

        @JvmStatic
        private val GIT_FETCH_DONE_REGEX = Regex("-> FETCH_HEAD")

        @JvmStatic
        private val WAIT_INTERVAL_MS = 10000L

        @JvmStatic
        private val WAIT_REPETITION = 50

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
    }

    private val serverUrl: String
    private val apiKey: String
    private val user: String

    private val secretKeys = listOf("serverUrl", "apiKey", "user")

    private val service: FossIdRestService

    // TODO: Parse the version from the FossID login page.
    override val version = ""

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

        val client = OkHttpClientHelper.buildClient()

        val retrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(serverUrl)
            .addConverterFactory(JacksonConverterFactory.create(FossIdRestService.JSON_MAPPER))
            .build()

        service = retrofit.create(FossIdRestService::class.java)
    }

    override fun filterOptionsForResult(options: Map<String, String>) =
        options.mapValues { (k, v) ->
            v.takeUnless { k in secretKeys }.orEmpty()
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
        outputDirectory: File,
        downloadDirectory: File
    ): Map<Package, List<ScanResult>> {
        val (results, duration) = measureTimedValue {
            val results = mutableMapOf<Package, MutableList<ScanResult>>()

            packages.forEach { pkg: Package ->
                val startTime = Instant.now()

                // TODO: Continue the processing of other packages and add an issue to the scan result.
                require(pkg.vcsProcessed.type == VcsType.GIT) { "FossID only supports Git repositories." }

                val url = pkg.vcsProcessed.url
                val revision = pkg.vcsProcessed.revision.ifEmpty { "HEAD" }
                val projectCode = convertGitUrlToProjectName(url)

                if (getProject(projectCode) == null) {
                    log.info { "Creating project '$projectCode' ..." }

                    service.createProject(user, apiKey, projectCode, projectCode)
                        .checkResponse("create project")
                }

                log.info { "Creating scan for URL $url and revision $revision ..." }

                val scanCode = createScan(projectCode, url, revision)

                log.info { "Initiating download from Git repository ..." }

                service.downloadFromGit(user, apiKey, scanCode)
                    .checkResponse("download data from Git", false)

                checkScan(scanCode)

                val rawResults = getRawResults(scanCode)
                val resultsSummary = createResultSummary(startTime, rawResults)

                results.getOrPut(pkg) { mutableListOf() } += resultsSummary
            }

            results
        }

        log.info { "Scan has been performed. Total time was ${duration.inSeconds}s." }

        return results
    }

    /**
     * Create a new scan in the FossID server and return the scan code.
     */
    private suspend fun createScan(projectCode: String, url: String, revision: String): String {
        val formatter = DateTimeFormatter.ofPattern("'$projectCode'_yyyyMMdd_HHmmss")
        val scanCode = formatter.format(LocalDateTime.now())

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
    private suspend fun checkScan(scanCode: String) {
        waitDownloadComplete(scanCode)

        val response = service.checkScanStatus(user, apiKey, scanCode)
            .checkResponse("check scan status", false)

        if (response.data?.state == ScanState.NOT_STARTED) {
            log.info { "Triggering scan as it has not yet been started." }

            service.runScan(user, apiKey, scanCode)
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
        val responseIdentifiedFiles = service.listIdentifiedFiles(user, apiKey, scanCode)
            .checkResponse("list identified files", false)

        val identifiedFiles = responseIdentifiedFiles.toList<IdentifiedFile>()
        log.info { "${identifiedFiles.size} identified files have been returned for scan code $scanCode." }

        val responseMarkedAsIdentified = service.listMarkedAsIdentifiedFiles(user, apiKey, scanCode)
            .checkResponse("list marked as identified files", false)
        val markedAsIdentifiedFiles = responseMarkedAsIdentified.toList<MarkedAsIdentifiedFile>()

        log.info {
            "${markedAsIdentifiedFiles.size} marked as identified files have been returned for scan code $scanCode."
        }

        // The "match_type=ignore" info is already in the ScanResult, but here we also get the ignore reason.
        val responseListIgnoredFiles = service.listIgnoredFiles(user, apiKey, scanCode)
            .checkResponse("list ignored files", false)

        val listIgnoredFiles = responseListIgnoredFiles.toList<IgnoredFile>()
        return RawResults(identifiedFiles, markedAsIdentifiedFiles, listIgnoredFiles)
    }

    /**
     * Construct the [ScanSummary] for this FossId scan.
     */
    private fun createResultSummary(startTime: Instant, rawResults: RawResults): ScanResult {
        val associate = rawResults.listIgnoredFiles.associateBy { it.path }

        val (filesCount, licenseFindings, copyrightFindings) = rawResults.markedAsIdentifiedFiles.ifEmpty {
            rawResults.identifiedFiles
        }.mapSummary(associate)

        val summary = ScanSummary(
            startTime = startTime,
            endTime = Instant.now(),
            fileCount = filesCount,
            packageVerificationCode = "",
            licenseFindings = licenseFindings.toSortedSet(),
            copyrightFindings = copyrightFindings.toSortedSet(),
            // TODO: Maybe get issues from FossId (see has_failed_scan_files, get_failed_files and maybe get_scan_log).
            issues = emptyList()
        )

        return ScanResult(UnknownProvenance, details, summary)
    }
}
