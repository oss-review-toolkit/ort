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

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.dos.DosClient
import org.ossreviewtoolkit.clients.dos.DosService
import org.ossreviewtoolkit.clients.dos.PackageInfo
import org.ossreviewtoolkit.clients.dos.ScanResultsResponseBody
import org.ossreviewtoolkit.downloader.DefaultWorkingTreeCache
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.utils.associateLicensesWithExceptions
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.model.utils.toPurlExtras
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.scanner.PackageScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.scanner.provenance.DefaultProvenanceDownloader
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.runBlocking

/**
 * The DOS scanner wrapper is a client for the scanner API implemented as part of the Double Open Server project at
 * https://github.com/doubleopen-project/dos. The server runs ScanCode in the backend and stores / reuses scan results
 * on a per-file basis and thus uses its own scan storage.
 */
@OrtPlugin(
    id = "DOS",
    displayName = "Double Open Server",
    description = "The DOS scanner wrapper is a client for the scanner API implemented as part of the Double Open " +
        "Server project at https://github.com/doubleopen-project/dos. The server runs ScanCode in the backend and " +
        "stores / reuses scan results on a per-file basis and thus uses its own scan storage.",
    factory = ScannerWrapperFactory::class
)
class DosScanner(
    override val descriptor: PluginDescriptor = DosScannerFactory.descriptor,
    private val config: DosScannerConfig
) : PackageScannerWrapper {
    // TODO: Introduce a DOS version and expose it through the API to use it here.
    override val version = "1.0.0"

    override val configuration = ""

    override val matcher: ScannerMatcher? = null

    override val readFromStorage = false
    override val writeToStorage = config.writeToStorage

    private val service =
        DosService.create(config.url, config.token.value, config.timeout?.let { Duration.ofSeconds(it) })
    internal val client = DosClient(service)

    override fun scanPackage(nestedProvenance: NestedProvenance?, context: ScanContext): ScanResult {
        val startTime = Instant.now()

        val scanResults = runBlocking {
            nestedProvenance?.root ?: run {
                logger.warn {
                    val cleanPurls = context.coveredPackages.joinToString { it.purl }
                    "Skipping scan as no provenance information is available for these packages: $cleanPurls"
                }

                return@runBlocking null
            }

            val packages = nestedProvenance.allProvenances.flatMap {
                context.coveredPackages.getDosPackages(it)
            }

            logger.info { "Packages requested for scanning: ${packages.joinToString { it.purl }}" }

            // Ask for scan results from DOS API
            val existingScanResults = runCatching {
                client.getScanResults(packages)
            }.onSuccess {
                if (it == null) throw ScanException("Missing scan results response body.")
            }.getOrThrow()

            when (existingScanResults?.state?.status) {
                "no-results" -> {
                    val downloader = DefaultProvenanceDownloader(DownloaderConfiguration(), DefaultWorkingTreeCache())

                    runCatching {
                        downloader.downloadRecursively(nestedProvenance)
                    }.mapCatching { sourceDir ->
                        runBackendScan(packages, sourceDir, startTime)
                    }.getOrThrow()
                }

                "pending" -> {
                    val jobId = checkNotNull(existingScanResults.state.jobId) {
                        "The job ID must not be null for 'pending' status."
                    }

                    pollForCompletion(packages.first(), jobId, "Pending scan", startTime)
                }

                "ready" -> existingScanResults

                else -> null
            }
        }

        val endTime = Instant.now()

        val scanResultsJson = scanResults?.results
        val summary = if (scanResultsJson != null) {
            generateSummary(startTime, endTime, scanResultsJson)
        } else {
            throw ScanException("Result from DOS is null.")
        }

        return ScanResult(
            nestedProvenance?.root ?: UnknownProvenance,
            details,
            summary.copy(licenseFindings = associateLicensesWithExceptions(summary.licenseFindings))
        )
    }

    internal suspend fun runBackendScan(
        packages: List<PackageInfo>,
        sourceDir: File,
        startTime: Instant
    ): ScanResultsResponseBody? {
        logger.info {
            "Initiating a backend scan for the following packages:\n${packages.joinToString("\n") { it.purl }}"
        }

        val tmpDir = createOrtTempDir()
        val zipName = "${sourceDir.name}.zip"
        val zipFile = tmpDir / zipName

        sourceDir.packZip(zipFile)
        sourceDir.safeDeleteRecursively()

        val uploadUrl = client.getUploadUrl(zipName)
        if (uploadUrl == null) {
            zipFile.delete()
            throw ScanException("Unable to get an upload URL for '$zipName'.")
        }

        val uploadSuccessful = client.uploadFile(zipFile, uploadUrl).also { zipFile.delete() }
        if (!uploadSuccessful) {
            throw ScanException("Uploading '$zipFile' to $uploadUrl failed.")
        }

        val jobResponse = client.addScanJob(zipName, packages)
        val id = jobResponse?.scannerJobId

        if (id == null) {
            throw ScanException(
                "Failed to add scan job for the following packages:\n${packages.joinToString("\n") { it.purl }}"
            )
        }

        logger.info { "Scan job added with ID '$id'." }

        // In case of multiple PURLs, they all point to packages with the same provenance. So if one package scan is
        // complete, all package scans are complete, which is why it is enough to arbitrarily pool for the first
        // package here.
        return pollForCompletion(packages.first(), id, "New scan", startTime)
    }

    private suspend fun pollForCompletion(
        pkg: PackageInfo,
        jobId: String,
        logMessagePrefix: String,
        startTime: Instant
    ): ScanResultsResponseBody? {
        while (true) {
            val jobState = client.getScanJobState(jobId) ?: return null

            logger.info {
                val duration = Duration.between(startTime, Instant.now()).toKotlinDuration()
                "$logMessagePrefix running for $duration, currently at ${jobState.state}."
            }

            when (jobState.state.status) {
                "completed" -> {
                    logger.info { "Scan completed for job with ID '$jobId'." }
                    return client.getScanResults(listOf(pkg))
                }

                "failed" -> {
                    throw ScanException("Scan failed for job with ID '$jobId': ${jobState.state.message}")
                }

                else -> delay(config.pollInterval.seconds)
            }
        }
    }
}

private fun Collection<Package>.getDosPackages(provenance: Provenance = UnknownProvenance): List<PackageInfo> {
    val extras = provenance.toPurlExtras()

    return when (provenance) {
        is RepositoryProvenance -> {
            // Maintain the VCS path to get the "bookmarking" right for the file tree in the package configuration UI.
            map {
                PackageInfo(
                    purl = it.id.toPurl(extras.qualifiers, it.vcsProcessed.path),
                    declaredLicenseExpressionSPDX = it.declaredLicensesProcessed.spdxExpression?.toString()
                )
            }
        }

        else -> map {
            PackageInfo(
                purl = it.id.toPurl(extras),
                declaredLicenseExpressionSPDX = it.declaredLicensesProcessed.spdxExpression?.toString()
            )
        }
    }
}
