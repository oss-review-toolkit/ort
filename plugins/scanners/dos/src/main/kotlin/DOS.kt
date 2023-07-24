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

    private val service = DOSService.create(config.serverUrl, config.serverToken)
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

        logger.info { "Package to scan: $pkg" }
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
                issues += createAndLogIssue(
                    source = name,
                    message = "Could not request scan results from DOS API",
                    severity = Severity.ERROR
                )
                return@runBlocking
            }

            when (scanResults?.state?.status) {
                /**
                 * No earlier scan results found from DOS database, initiate a new scan.
                 */
                "no-results" -> {
                    logger.info { "Initiating a backend scan" }

                    // Zip the packet to scan
                    val zipName = dosDir.name + ".zip"
                    val targetZipFile = File("$tmpDir$zipName")
                    dosDir.packZip(targetZipFile)

                    // Request presigned URL from DOS API
                    val presignedUrl = repository.getPresignedUrl(zipName)
                    if (presignedUrl == null) {
                        issues += createAndLogIssue(
                            source = name,
                            message = "Could not get a presigned URL for this package",
                            severity = Severity.ERROR
                        )
                        return@runBlocking
                    }

                    // Transfer the zipped packet to S3 Object Storage and do local cleanup
                    val uploadSuccessful = repository.uploadFile(presignedUrl, tmpDir + zipName)
                    if (uploadSuccessful) {
                        deleteFileOrDir(dosDir)
                    } else {
                        issues += createAndLogIssue(
                            source = name,
                            message = "Could not upload the packet to S3",
                            severity = Severity.ERROR
                        )
                        deleteFileOrDir(dosDir)
                        return@runBlocking
                    }

                    // Notify DOS API about the new zipped file at S3, and get the unzipped folder
                    // name as a return
                    logger.info { "Zipped file at S3: $zipName" }
                    val packageResponse = repository.getScanFolder(zipName, pkg.purl)
                    if (packageResponse != null) {
                        deleteFileOrDir(targetZipFile)
                    } else {
                        issues += createAndLogIssue(
                            source = name,
                            message = "Could not get the scan folder name from DOS API",
                            severity = Severity.ERROR
                        )
                        deleteFileOrDir(targetZipFile)
                        return@runBlocking
                    }

                    // Send the scan job to DOS API to start the backend scanning
                    val jobResponse = packageResponse.folderName?.let { repository.postScanJob(it, packageResponse.packageId) }
                    val id = jobResponse?.scannerJob?.id
                    if (jobResponse != null) {
                        logger.info { "New scan request: $jobResponse" }
                    } else {
                        issues += createAndLogIssue(
                            source = name,
                            message = "Could not create a new scan job at DOS API",
                            severity = Severity.ERROR
                        )
                        return@runBlocking
                    }

                    // Poll the job state periodically and log
                    while (true) {
                        val jobState = id?.let { repository.getJobState(it) }
                        logger.info { "Elapsed time: ${elapsedTime(thisScanStartTime)}/${elapsedTime(totalScanStartTime)}, state = $jobState" }
                        if (jobState == "completed") {
                            scanResults = repository.getScanResults(pkg.purl)
                            break
                        } else {
                            delay(config.pollInterval * 1000L)
                        }
                    }
                }

                /**
                 * No earlier results found from DOS database, but a scan for this package is
                 * either pending or ongoing, so need to monitor the scanning process until completed,
                 * and then return the results.
                 */
                "pending" -> {
                    val idPendingJob = scanResults?.state?.id
                    while (true) {
                        val jobState = idPendingJob?.let { repository.getJobState(it) }
                        logger.info { "Pending scan: ${elapsedTime(thisScanStartTime)}/${elapsedTime(totalScanStartTime)}, state = $jobState" }
                        if (jobState == "completed") {
                            scanResults = repository.getScanResults(pkg.purl)
                            break
                        } else {
                            delay(config.pollInterval * 1000L)
                        }
                    }
                }

                /**
                 * Scan results for this package found from the database. Do local cleaning.
                 */
                "ready" -> {
                    deleteFileOrDir(dosDir)
                }
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

        return ScanResult(
            provenance,
            details,
            summary
        )
    }
}
