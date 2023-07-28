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

import org.apache.logging.log4j.kotlin.Logging
import org.ossreviewtoolkit.clients.dos.*
import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.*
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

/**
 * DOS scanner is the ORT implementation of a ScanCode-based backend scanner, and it is a part of
 * DoubleOpen project: https://github.com/doubleopen-project/dos
 *
 * Copyright (c) 2023 HH Partners
 */
class DOS internal constructor(
    override val name: String,
    private val scannerConfig: ScannerConfiguration,
    private val config: DOSConfig
) : PackageScannerWrapper {
    private companion object : Logging
    private val downloaderConfig = DownloaderConfiguration()

    class Factory : AbstractScannerWrapperFactory<DOS>("DOS") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            DOS(type, scannerConfig, DOSConfig.create(scannerConfig))
    }

    override val details: ScannerDetails
        get() = ScannerDetails(name, version, configuration)
    override val criteria: ScannerCriteria? = null
    override val configuration = ""
    override val version = "1.0"

    private val service = DOSService.create(config.serverUrl, config.serverToken, config.restTimeout)
    private val repository = DOSRepository(service)
    private val totalScanStartTime = Instant.now()

    private fun createSingleIssueSummary(
        startTime: Instant,
        endTime: Instant = Instant.now(),
        issue: Issue
    ) = ScanSummary.EMPTY.copy(
        startTime = startTime,
        endTime = endTime,
        issues = listOf(issue)
    )

    override fun scanPackage(pkg: Package, context: ScanContext): ScanResult {
        val thisScanStartTime = Instant.now()
        val tmpDir = "/tmp/"
        val provenance: Provenance
        val summary: ScanSummary
        val issues = mutableListOf<Issue>()

        logger.info { "Package to scan: ${pkg.purl}" }
        // Use ORT specific local file structure
        val dosDir = createOrtTempDir()
        var scanResults: DOSService.ScanResultsResponseBody?

        runBlocking {
            // Download the package
            val downloader = Downloader(downloaderConfig)
            provenance = downloader.download(pkg, dosDir)
            logger.info { "Package downloaded to: $dosDir" }

            // Ask for scan results from DOS API
            scanResults = repository.getScanResults(pkg.purl)
            if (scanResults == null) {
                issues.add(createAndLogIssue(name, "Could not request scan results from DOS API", Severity.ERROR))
                return@runBlocking
            }

            when (scanResults?.state?.status) {
                "no-results" -> {
                    scanResults = runBackendScan(
                        pkg,
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
                "pending" -> scanResults?.state?.id?.let { waitForPendingScan(pkg, it, thisScanStartTime) }
                "ready" -> deleteFileOrDir(dosDir)
            }
        }
        val thisScanEndTime = Instant.now()

        /**
         * Handle gracefully non-successful calls to DOS backend and log issues for failing tasks
         */
        summary = if (scanResults != null) {
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
        return ScanResult(provenance, details, summary)
    }

    private suspend fun runBackendScan(
        pkg: Package,
        dosDir: File,
        tmpDir: String,
        thisScanStartTime: Instant,
        issues: MutableList<Issue>): DOSService.ScanResultsResponseBody? {

        logger.info { "Initiating a backend scan" }

        // Zip the packet to scan
        val zipName = dosDir.name + ".zip"
        val targetZipFile = File("$tmpDir$zipName")
        dosDir.packZip(targetZipFile)

        // Request presigned URL from DOS API
        val presignedUrl = repository.getPresignedUrl(zipName)
        if (presignedUrl == null) {
            issues.add(createAndLogIssue(name, "Could not get a presigned URL for this package", Severity.ERROR))
            return DOSService.ScanResultsResponseBody(DOSService.ScanResultsResponseBody.State("failed"))
        }

        // Transfer the zipped packet to S3 Object Storage and do local cleanup
        val uploadSuccessful = repository.uploadFile(presignedUrl, tmpDir + zipName)
        if (uploadSuccessful) {
            deleteFileOrDir(dosDir)
        } else {
            issues.add(createAndLogIssue(name, "Could not upload the packet to S3", Severity.ERROR))
            deleteFileOrDir(dosDir)
            return DOSService.ScanResultsResponseBody(DOSService.ScanResultsResponseBody.State("failed"))
        }

        // Notify DOS API about the new zipped file at S3, and get package ID as a return
        logger.info { "Zipped file at S3: $zipName" }
        val packageResponse = repository.getPackageId(zipName, pkg.purl)
        if (packageResponse != null) {
            deleteFileOrDir(targetZipFile)
        } else {
            issues.add(createAndLogIssue(name, "Could not get the package ID from DOS API", Severity.ERROR))
            deleteFileOrDir(targetZipFile)
            return DOSService.ScanResultsResponseBody(DOSService.ScanResultsResponseBody.State("failed"))
        }

        // Send the scan job to DOS API to start the backend scanning
        val jobResponse = packageResponse.packageId.let { repository.postScanJob(packageResponse.packageId) }
        val id = jobResponse?.scannerJobId
        if (jobResponse != null) {
            logger.info { "New scan request: ${pkg.purl}" }
            if (jobResponse.message == "Adding job to queue was unsuccessful") {
                issues.add(createAndLogIssue(name, "DOS API: 'unsuccessful' response to the scan job request", Severity.ERROR))
                return DOSService.ScanResultsResponseBody(DOSService.ScanResultsResponseBody.State("failed"))
            }
        } else {
            issues.add(createAndLogIssue(name, "Could not create a new scan job at DOS API", Severity.ERROR))
            return DOSService.ScanResultsResponseBody(DOSService.ScanResultsResponseBody.State("failed"))
        }

        return pollForCompletion(pkg, id, "New scan request", thisScanStartTime)
    }

    private suspend fun waitForPendingScan(
        pkg: Package,
        id: String,
        thisScanStartTime: Instant): DOSService.ScanResultsResponseBody? {
        return pollForCompletion(pkg, id, "Pending scan", thisScanStartTime)
    }

    private suspend fun pollForCompletion(
        pkg: Package,
        jobId: String?,
        logMessagePrefix: String,
        thisScanStartTime: Instant): DOSService.ScanResultsResponseBody? {
        while (true) {
            val jobState = jobId?.let { repository.getJobState(it) }
            logger.info {
                "$logMessagePrefix: ${elapsedTime(thisScanStartTime)}/${elapsedTime(totalScanStartTime)}, state = $jobState"
            }
            if (jobState == "completed") {
                return repository.getScanResults(pkg.purl)
            } else {
                delay(config.pollInterval * 1000L)
            }
        }
    }
}
