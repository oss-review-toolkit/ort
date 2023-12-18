/*
 * SPDX-FileCopyrightText: 2023 HH Partners
 *
 * SPDX-License-Identifier: MIT
 */

package org.ossreviewtoolkit.plugins.scanners.dos

import java.io.File
import java.time.Instant

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

import org.apache.logging.log4j.kotlin.logger

import org.jetbrains.annotations.VisibleForTesting
import org.ossreviewtoolkit.clients.dos.*
import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.scanner.*
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.model.utils.associateLicensesWithExceptions
import org.ossreviewtoolkit.utils.common.Options
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.spdx.toSpdx

/**
 * DOS scanner is the ORT implementation of a ScanCode-based backend scanner, and it is a part of
 * DoubleOpen project: https://github.com/doubleopen-project/dos
 *
 * Copyright (c) 2023 HH Partners
 */
class DOS internal constructor(
    override val name: String,
    private val config: DOSConfig, override val readFromStorage: Boolean, override val writeToStorage: Boolean
) : PackageScannerWrapper {
    private val downloaderConfig = DownloaderConfiguration()

    class Factory : ScannerWrapperFactory<DOSConfig>("DOS") {
        override fun create(config: DOSConfig, wrapperConfig: ScannerWrapperConfig) =
            DOS(type, config, readFromStorage = false, writeToStorage = false)

        override fun parseConfig(options: Options, secrets: Options) = DOSConfig.create(options, secrets)
    }

    override val matcher: ScannerMatcher? = null
    override val configuration = ""
    // Later on, use DOS API to return API's version and use it here
    override val version = "1.0"

    private val service = DOSService.create(config.serverUrl, config.serverToken, config.restTimeout)
    var repository = DOSRepository(service)
    private val totalScanStartTime = Instant.now()

    override fun scanPackage(pkg: Package, context: ScanContext): ScanResult {
        val thisScanStartTime = Instant.now()
        val tmpDir = "/tmp/"

        val summary: ScanSummary
        val issues = mutableListOf<Issue>()
        var scanResults: DOSService.ScanResultsResponseBody?
        val purls = context.coveredPackages.getDosPurls()

        // Decide which provenance type this package is
        val provenance = if (pkg.vcsProcessed != VcsInfo.EMPTY && pkg.vcsProcessed.revision != "") {
            RepositoryProvenance(pkg.vcsProcessed, pkg.vcsProcessed.revision)
        } else if (pkg.sourceArtifact != RemoteArtifact.EMPTY) {
            ArtifactProvenance(pkg.sourceArtifact)
        } else {
            UnknownProvenance
        }

        logger.info { "Package to scan: ${pkg.purl}" }

        runBlocking {
            // Ask for scan results from DOS API
            scanResults = repository.getScanResults(purls, config.fetchConcluded)
            if (scanResults == null) {
                issues.add(createAndLogIssue(name, "Could not request scan results from DOS API", Severity.ERROR))
                return@runBlocking
            }
            when (scanResults?.state?.status) {
                "no-results" -> {
                    // Download the package to an ORT specific local file structure
                    val dosDir = createOrtTempDir()
                    val downloader = Downloader(downloaderConfig)
                    downloader.download(pkg, dosDir)
                    logger.info { "Package downloaded to: $dosDir" }

                    // Start backend scanning
                    scanResults = runBackendScan(
                        purls,
                        dosDir,
                        tmpDir,
                        thisScanStartTime,
                        issues
                    )
                    if (scanResults == null || scanResults!!.state.status == "failed") {
                        logger.error { "Something went wrong at DOS backend, exiting scan of this package" }
                        return@runBlocking
                    }
                }
                "pending" -> scanResults?.state?.jobId?.let { waitForPendingScan(purls.first(), it, thisScanStartTime) }
                "ready" -> { /* Results exist, form an ORT result and move on to the next package */ }
            }
        }
        val thisScanEndTime = Instant.now()

        /**
         * Handle gracefully non-successful calls to DOS backend and log issues for failing tasks
         */
        summary = if (scanResults?.results != null) {
            generateSummary(
                thisScanStartTime,
                thisScanEndTime,
                scanResults?.results.toString()
            )
        } else {
            ScanSummary(
                thisScanStartTime,
                thisScanEndTime,
                emptySet(),
                emptySet(),
                emptySet(),
                issues)
        }

        val fixedUpLicenses = associateLicensesWithExceptions(summary.licenseFindings).mapTo(mutableSetOf()) {
            // TODO: Remove this again once fixed upstream in ORT.
            it.copy(
                license = it.license.toString().replace(
                    "GPL-2.0-only AND Classpath-exception-2.0",
                    "GPL-2.0-only WITH Classpath-exception-2.0"
                ).toSpdx()
            )
        }

        return ScanResult(
            provenance,
            details,
            summary.copy(licenseFindings = fixedUpLicenses)
        )
    }

    @VisibleForTesting
    internal suspend fun runBackendScan(
        purls: List<String>,
        dosDir: File,
        tmpDir: String,
        thisScanStartTime: Instant,
        issues: MutableList<Issue>): DOSService.ScanResultsResponseBody? {

        logger.info { "Initiating a backend scan" }

        // Zip the packet to scan and do local cleanup
        val zipName = dosDir.name + ".zip"
        val targetZipFile = File("$tmpDir$zipName")
        dosDir.packZip(targetZipFile)
        deleteFileOrDir(dosDir)  // ORT temp directory not needed anymore

        // Request presigned URL from DOS API
        val presignedUrl = repository.getPresignedUrl(zipName)
        if (presignedUrl == null) {
            issues.add(createAndLogIssue(name, "Could not get a presigned URL for this package", Severity.ERROR))
            deleteFileOrDir(targetZipFile)  // local cleanup before returning
            return DOSService.ScanResultsResponseBody(DOSService.ScanResultsResponseBody.State("failed"))
        }

        // Transfer the zipped packet to S3 Object Storage and do local cleanup
        val uploadSuccessful = repository.uploadFile(presignedUrl, tmpDir + zipName)
        if (!uploadSuccessful) {
            issues.add(createAndLogIssue(name, "Could not upload the packet to S3", Severity.ERROR))
            deleteFileOrDir(targetZipFile)  // local cleanup before returning
            return DOSService.ScanResultsResponseBody(DOSService.ScanResultsResponseBody.State("failed"))
        }
        deleteFileOrDir(targetZipFile)  // make sure the zipped packet is always deleted locally

        // Send the scan job to DOS API to start the backend scanning and do local cleanup
        val jobResponse = repository.postScanJob(zipName, purls)
        val id = jobResponse?.scannerJobId

        if (jobResponse != null) {
            logger.info { "New scan request: Packages = ${purls.joinToString()}, Zip file = $zipName" }
            if (jobResponse.message == "Adding job to queue was unsuccessful") {
                issues.add(createAndLogIssue(name, "DOS API: 'unsuccessful' response to the scan job request", Severity.ERROR))
                return DOSService.ScanResultsResponseBody(DOSService.ScanResultsResponseBody.State("failed"))
            }
        } else {
            issues.add(createAndLogIssue(name, "Could not create a new scan job at DOS API", Severity.ERROR))
            return DOSService.ScanResultsResponseBody(DOSService.ScanResultsResponseBody.State("failed"))
        }

        return id?.let {
            // In case of multiple PURLs, they all point to packages with the same provenance. So if one package scan is
            // complete, all package scans are complete, which is why it is enough to arbitrarily pool for the first
            // package here.
            pollForCompletion(purls.first(), it, "New scan", thisScanStartTime)
        }
    }

    private suspend fun waitForPendingScan(
        purl: String,
        id: String,
        thisScanStartTime: Instant): DOSService.ScanResultsResponseBody? {
        return pollForCompletion(purl, id, "Pending scan", thisScanStartTime)
    }

    private suspend fun pollForCompletion(
        purl: String,
        jobId: String,
        logMessagePrefix: String,
        thisScanStartTime: Instant): DOSService.ScanResultsResponseBody? {
        while (true) {
            val jobState = repository.getJobState(jobId)
            if (jobState != null) {
                logger.info {
                    "$logMessagePrefix: ${elapsedTime(thisScanStartTime)}/${elapsedTime(totalScanStartTime)}, " +
                            "state = ${jobState.state.status}, " +
                            "message = ${jobState.state.message}"
                }
            }
            if (jobState != null) {
                when (jobState.state.status) {
                    "completed" -> {
                        logger.info { "Scan completed" }
                        return repository.getScanResults(listOf(purl), config.fetchConcluded)
                    }
                    "failed" -> {
                        logger.error { "Scan failed" }
                        return null
                    }
                    else -> delay(config.pollInterval * 1000L)
                }
            }
        }
    }
}

private fun Collection<Package>.getDosPurls(): List<String> =
    map { pkg ->
        pkg.purl.takeUnless { pkg.vcsProcessed.path.isNotEmpty() }
            // Encode a path within the source code to the PURL.
            ?: "${pkg.purl}#${pkg.vcsProcessed.path}"
    }
