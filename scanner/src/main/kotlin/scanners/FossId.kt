/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners

import com.fasterxml.jackson.databind.JsonNode

import java.io.File
import java.time.Duration
import java.time.Instant

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

import org.ossreviewtoolkit.fossid.FossIdRestClient
import org.ossreviewtoolkit.fossid.api.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.fossid.api.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.fossid.api.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.fossid.api.result.FossIdScanResult
import org.ossreviewtoolkit.fossid.api.status.ScanState
import org.ossreviewtoolkit.fossid.api.summary.Summarizable
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.RemoteScanner
import org.ossreviewtoolkit.utils.log

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
class FossId(name: String, config: ScannerConfiguration) : RemoteScanner(name, config) {
    class Factory : AbstractScannerFactory<FossId>("FossId") {
        override fun create(config: ScannerConfiguration) = FossId(scannerName, config)
    }

    companion object {
        @JvmStatic
        private val projectNamePattern = """^.*\/([\w-]+)\.git$""".toRegex()

        /**
         * Convert a GIT repo URL to a valid project name :
         * https://github.com/jshttp/mime-types.git -> mime-types
         */
        fun convertGitUrlToProjectName(gitRepoUrl: String): String {
            val projectNameMatcher = projectNamePattern.matchEntire(gitRepoUrl)
            requireNotNull(projectNameMatcher) {
                "GIT repo url '$gitRepoUrl' cannot be matched"
            }
            require(projectNameMatcher.groupValues.size == 2) {
                "Project name cannot be extracted from GIT repo url '$gitRepoUrl'"
            }

            val projectCode = projectNameMatcher.groupValues[1]
            log.info { "Project code for url='$gitRepoUrl is '$projectCode'" }
            return projectCode
        }
    }

    private val serverUrl: String
    private val apiKey: String
    private val user: String
    private val client: FossIdRestClient
    private val fossIdVersion: String

    init {
        val localOptions = config.options

        require(localOptions != null && localOptions["FossId"] != null) {
            "Missing FossId Scanner configuration"
        }

        val fossIdScannerOptions = localOptions.getValue("FossId")

        serverUrl = fossIdScannerOptions["serverUrl"] ?: throw IllegalArgumentException("Missing Server Url")
        apiKey = fossIdScannerOptions["apiKey"] ?: throw IllegalArgumentException("Missing API Key")
        user = fossIdScannerOptions["user"] ?: throw IllegalArgumentException("Missing User")

        client = FossIdRestClient(user, apiKey, serverUrl)
        fossIdVersion = client.getVersion().orEmpty()
    }

    /**
     * Returns the scanner configuration to be included in the scanner results
     *
     * In the case of FossId, sensitive information will be redacted
     */
    override fun getConfigForResult(): ScannerConfiguration {
        if (config.options == null || config.options?.get("FossId") == null) {
            return config
        }
        val mutableOptions = config.options?.toMutableMap()
        mutableOptions?.get("FossId")?.toMutableMap()?.let {
            val secret = "X".repeat(6)
            it.computeIfPresent("serverUrl") { _, _ -> secret }
            it.computeIfPresent("apiKey") { _, _ -> secret }
            it.computeIfPresent("user") { _, _ -> secret }
            mutableOptions.put("FossId", it)
        }
        return config.copy(options = mutableOptions)
    }

    override fun getConfiguration(): String {
        // sanitized configuration to hide sensitive information
        val secret = "X".repeat(6)
        return "--serverUrl=$secret --apiKey=$secret --user=$secret"
    }

    override fun getVersion(): String {
        return fossIdVersion
    }

    override suspend fun scanPackages(
            packages: List<Package>,
            outputDirectory: File,
            downloadDirectory: File
    ): Map<Package, List<ScanResult>> {
        val globalStartTime = Instant.now()
        val results = mutableMapOf<Package, MutableList<ScanResult>>()

        packages.forEach { pkg: Package ->
            coroutineScope {
                launch {
                    results.computeIfAbsent(pkg) { mutableListOf() }

                    val startTime = Instant.now()

                    val url = pkg.vcsProcessed.url
                    val revision = pkg.vcsProcessed.revision
                    val projectCode = convertGitUrlToProjectName(url)
                    val scanCode = kotlin.run {
                        val project = client.getProject(projectCode)
                        if (project == null) {
                            client.createProject(projectCode)
                        }
                        val scans = client.listScansForProject(projectCode)
                        val existingScan = scans.sortedByDescending { scan -> scan.id }.find { scan ->
                            scan.gitBranch == revision && scan.gitRepoUrl == url
                        }
                        if (existingScan == null) {
                            this@FossId.log.info { "No scan found for url=$url and revision=$revision" }
                            val scanCode = client.createScan(projectCode, url, revision)
                            client.downloadFromGit(scanCode)
                            scanCode
                        } else {
                            this@FossId.log.info { "Scan found for url=$url and revision=$revision" }

                            val scanCode = existingScan.code
                            requireNotNull(scanCode) {
                                "FossId returned a null scancode for an existing scan"
                            }
                            // it can be that the scan exists, but has not been triggered yet
                            scanCode
                        }
                    }
                    checkScan(scanCode)
                    val rawResults = getRawResults(scanCode)
                    val resultsSummary = createResultSummary(startTime, rawResults)
                    results[pkg]?.add(resultsSummary)
                }
            }
        }
        val elapsedTime = Duration.between(globalStartTime, Instant.now())
        log.info { "Scan have been performed. Total time is ${elapsedTime.toMillis()}ms" }
        return results
    }

    /**
     * Get the scan status and run it if needed
     */
    private suspend fun checkScan(scanCode: String) {
        client.waitDownloadComplete(scanCode)
        val scanStatus = client.getScanStatus(scanCode)
        if (scanStatus?.state == ScanState.NOT_STARTED) {
            client.runScan(scanCode)
            client.waitScanComplete(scanCode)
        }
    }

    /**
     * A simple Quad bean to hold FossId raw results
     */
    private data class RawResults(
            val results: List<FossIdScanResult>,
            val identifiedFiles: List<IdentifiedFile>,
            val markedAsIdentifiedFiles: List<MarkedAsIdentifiedFile>,
            val listIgnoredFiles: List<IgnoredFile>
    )

    /**
     * Get the different kind of results from the FossId Server
     */
    private fun getRawResults(scanCode: String): RawResults {
        val scanResults = client.listScanResults(scanCode)
        val identifiedFiles = client.listIdentifiedFiles(scanCode)
        val markedAsIdentifiedFiles = client.listMarkedAsIdentifiedFiles(scanCode)
        // the match_type=ignore info is already in scanResults, but here we also get the ignore reason
        val listIgnoredFiles = client.listIgnoredFiles(scanCode)
        return RawResults(scanResults, identifiedFiles, markedAsIdentifiedFiles, listIgnoredFiles)
    }

    /**
     * Construct the [ScanSummary] for this FossId scan
     */
    private fun createResultSummary(startTime: Instant, rawResults: RawResults): ScanResult {
        val associate = rawResults.listIgnoredFiles.associateBy { it.path }

        val (filesCount, licenseFindings, copyrightFindings) = kotlin.run {
            if (rawResults.markedAsIdentifiedFiles.isNotEmpty()) {
                rawResults.markedAsIdentifiedFiles.mapSummary(associate)
            } else {
                rawResults.identifiedFiles.mapSummary(associate)
            }
        }

        val summary = ScanSummary(
                startTime = startTime,
                endTime = Instant.now(),
                fileCount = filesCount,
                packageVerificationCode = "",
                licenseFindings = licenseFindings.toSortedSet(),
                copyrightFindings = copyrightFindings.toSortedSet(),
                // TODO get issues from FossId ? has_failed_scan_files, get_failed_files and maybe get_scan_log
                issues = emptyList()
        )

        val rawResultsAsJson = jsonMapper.valueToTree<JsonNode>(rawResults)
        return ScanResult(Provenance(), getDetails(), summary, rawResultsAsJson)
    }

    /**
     * A simple Triple bean to hold FossId mapped results
     */
    private data class FindingsContainer(
            val filesCount: Int,
            val licenseFindings: MutableList<LicenseFinding>,
            val copyrightFindings: MutableList<CopyrightFinding>
    )

    /**
     * Map a fossId Raw result to sections that can be included in a [ScanSummary]
     */
    private fun <T> List<T>.mapSummary(ignoredFiles: Map<String, IgnoredFile>): FindingsContainer
            where T : Summarizable {
        val licenseFindings = mutableListOf<LicenseFinding>()
        val copyrightFindings = mutableListOf<CopyrightFinding>()
        var count = 0

        forEach { summarizable: Summarizable ->
            if (!ignoredFiles.contains(summarizable.getFileName())) {
                val summary = summarizable.toSummary()
                val location = TextLocation(summary.path, -1, -1)
                count++

                summary.licences.forEach {
                    val finding = LicenseFinding(it.identifier, location)
                    licenseFindings.add(finding)
                }

                summarizable.getCopyright()?.let {
                    if (it.isNotEmpty()) {
                        copyrightFindings.add(CopyrightFinding(it, location))
                    }
                }
            }
        }
        return FindingsContainer(
                filesCount = count,
                licenseFindings = licenseFindings,
                copyrightFindings = copyrightFindings
        )
    }
}
