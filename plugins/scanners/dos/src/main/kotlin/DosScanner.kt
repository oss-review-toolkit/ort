/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.dos

import java.io.File
import java.time.Duration
import java.time.Instant

import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.dos.DosClient
import org.ossreviewtoolkit.clients.dos.DosService
import org.ossreviewtoolkit.clients.dos.ScanResultsResponseBody
import org.ossreviewtoolkit.downloader.DefaultWorkingTreeCache
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.associateLicensesWithExceptions
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.model.utils.toPurlExtras
import org.ossreviewtoolkit.scanner.PackageScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperConfig
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.scanner.provenance.DefaultProvenanceDownloader
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.utils.common.Options
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

/**
 * The DOS scanner wrapper is a client for the scanner API implemented as part of the Double Open Server project at
 * https://github.com/doubleopen-project/dos. The server runs ScanCode in the backend and stores / reuses scan results
 * on a per-file basis and thus uses its own scan storage.
 */
class DosScanner internal constructor(
    override val name: String,
    private val config: DosScannerConfig,
    override val readFromStorage: Boolean,
    override val writeToStorage: Boolean
) : PackageScannerWrapper {
    class Factory : ScannerWrapperFactory<DosScannerConfig>("DOS") {
        override fun create(config: DosScannerConfig, wrapperConfig: ScannerWrapperConfig) =
            DosScanner(type, config, readFromStorage = false, writeToStorage = false)

        override fun parseConfig(options: Options, secrets: Options) = DosScannerConfig.create(options, secrets)
    }

    override val matcher: ScannerMatcher? = null
    override val configuration = ""

    // TODO: Introduce a DOS version and expose it through the API to use it here.
    override val version = "1.0.0"

    private val service = DosService.create(config.url, config.token, config.timeout?.let { Duration.ofSeconds(it) })
    internal val client = DosClient(service)

    override fun scanPackage(nestedProvenance: NestedProvenance?, context: ScanContext): ScanResult {
        val startTime = Instant.now()

        val issues = mutableListOf<Issue>()

        val scanResults = runBlocking {
            val provenance = nestedProvenance?.root ?: run {
                logger.warn {
                    val cleanPurls = context.coveredPackages.joinToString { it.purl }
                    "Skipping scan as no provenance information is available for these packages: $cleanPurls"
                }

                return@runBlocking null
            }

            val purls = context.coveredPackages.getDosPurls(provenance)

            logger.info { "Packages requested for scanning: ${purls.joinToString()}" }

            // Ask for scan results from DOS API
            val existingScanResults = runCatching {
                client.getScanResults(purls, config.fetchConcluded)
            }.onFailure {
                issues += createAndLogIssue(name, it.collectMessages())
            }.onSuccess {
                if (it == null) issues += createAndLogIssue(name, "Missing scan results response body.")
            }.getOrNull()

            when (existingScanResults?.state?.status) {
                "no-results" -> {
                    val downloader = DefaultProvenanceDownloader(DownloaderConfiguration(), DefaultWorkingTreeCache())

                    runCatching {
                        downloader.download(provenance)
                    }.mapCatching { sourceDir ->
                        runBackendScan(purls, sourceDir, startTime, issues)
                    }.onFailure {
                        issues += createAndLogIssue(name, it.collectMessages())
                    }.getOrNull()
                }

                "pending" -> {
                    val jobId = checkNotNull(existingScanResults.state.jobId) {
                        "The job ID must not be null for 'pending' status."
                    }

                    pollForCompletion(purls.first(), jobId, "Pending scan", startTime, issues)
                }

                "ready" -> existingScanResults

                else -> null
            }
        }

        val endTime = Instant.now()

        val scanResultsJson = scanResults?.results
        val summary = if (scanResultsJson != null) {
            val parsedSummary = generateSummary(startTime, endTime, scanResultsJson)
            parsedSummary.copy(issues = parsedSummary.issues + issues)
        } else {
            ScanSummary.EMPTY.copy(startTime = startTime, endTime = endTime, issues = issues)
        }

        return ScanResult(
            nestedProvenance?.root ?: UnknownProvenance,
            details,
            summary.copy(licenseFindings = associateLicensesWithExceptions(summary.licenseFindings))
        )
    }

    internal suspend fun runBackendScan(
        purls: List<String>,
        sourceDir: File,
        startTime: Instant,
        issues: MutableList<Issue>
    ): ScanResultsResponseBody? {
        logger.info { "Initiating a backend scan for $purls." }

        val tmpDir = createOrtTempDir()
        val zipName = "${sourceDir.name}.zip"
        val zipFile = tmpDir.resolve(zipName)

        sourceDir.packZip(zipFile)
        sourceDir.safeDeleteRecursively(force = true)

        val uploadUrl = client.getUploadUrl(zipName)
        if (uploadUrl == null) {
            issues += createAndLogIssue(name, "Unable to get an upload URL for '$zipName'.")
            zipFile.delete()
            return null
        }

        val uploadSuccessful = client.uploadFile(zipFile, uploadUrl).also { zipFile.delete() }
        if (!uploadSuccessful) {
            issues += createAndLogIssue(name, "Uploading '$zipFile' to $uploadUrl failed.")
            return null
        }

        val jobResponse = client.addScanJob(zipName, purls)
        val id = jobResponse?.scannerJobId

        if (id == null) {
            issues += createAndLogIssue(name, "Failed to add scan job for '$zipName' and $purls.")
            return null
        }

        // In case of multiple PURLs, they all point to packages with the same provenance. So if one package scan is
        // complete, all package scans are complete, which is why it is enough to arbitrarily pool for the first
        // package here.
        return pollForCompletion(purls.first(), id, "New scan", startTime, issues)
    }

    private suspend fun pollForCompletion(
        purl: String,
        jobId: String,
        logMessagePrefix: String,
        startTime: Instant,
        issues: MutableList<Issue>
    ): ScanResultsResponseBody? {
        while (true) {
            val jobState = client.getScanJobState(jobId) ?: return null

            logger.info {
                val duration = Duration.between(startTime, Instant.now()).toKotlinDuration()
                "$logMessagePrefix running for $duration, currently at ${jobState.state}."
            }

            when (jobState.state.status) {
                "completed" -> {
                    logger.info { "Scan completed" }
                    return client.getScanResults(listOf(purl), config.fetchConcluded)
                }

                "failed" -> {
                    issues += createAndLogIssue(name, "Scan failed in DOS API")
                    return null
                }

                else -> delay(config.pollInterval.seconds)
            }
        }
    }
}

private fun Collection<Package>.getDosPurls(provenance: Provenance = UnknownProvenance): List<String> {
    val extras = provenance.toPurlExtras()

    return when (provenance) {
        is RepositoryProvenance -> {
            // Maintain the VCS path to get the "bookmarking" right for the file tree in the package configuration UI.
            map { it.id.toPurl(extras.qualifiers, it.vcsProcessed.path) }
        }

        else -> map { it.id.toPurl(extras) }
    }
}
