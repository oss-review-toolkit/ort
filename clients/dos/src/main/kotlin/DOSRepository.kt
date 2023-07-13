/*
 * SPDX-FileCopyrightText: 2023 HH Partners
 *
 * SPDX-License-Identifier: MIT
 */

package org.ossreviewtoolkit.clients.dos

import java.io.File

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

import org.ossreviewtoolkit.clients.dos.DOSService.Companion.logger

/**
 * This class implements the data layer of the DOS client.
 */
class DOSRepository(private val dosService: DOSService) {
    /**
     * Get S3 presigned URL from DOS API to upload a package for scanning.
     */
    suspend fun getPresignedUrl(key: String): String? {
        if (key.isEmpty()) {
            logger.error { "Need the name of the zipped packet to upload" }
            return null
        }
        val requestBody = DOSService.PresignedUrlRequestBody(key)
        val response = dosService.getPresignedUrl(requestBody)

        return if (response.isSuccessful) {
            if (response.body()?.success == true) {
                logger.info { "Presigned URL from API: ${response.body()?.presignedUrl}" }
                response.body()?.presignedUrl
            } else {
                logger.error { "Error msg from API: ${response.body()?.message}" }
                null
            }
        } else {
            logger.error { "$response" }
            null
        }
    }

    /**
     * Upload a file to S3, using presigned URL.
     */
    suspend fun uploadFile(presignedUrl: String, filePath: String): Boolean {
        val file = File(filePath)
        val requestBody = file.readBytes().toRequestBody("application/zip".toMediaType())
        val response = dosService.putS3File(presignedUrl, requestBody)

        return if (response.isSuccessful) {
            logger.info { "Packet successfully uploaded to S3" }
            true
        } else {
            logger.error { "Failed to upload packet to S3: $response" }
            false
        }
    }

    /**
     * Request earlier scan results from DOS API, using Package URL for
     * identifying the package.
     */
    suspend fun getScanResults(purl: String): String? {
        if (purl.isEmpty()) {
            logger.error { "Need the package URL to check for scan results" }
            return null
        }
        val requestBody = DOSService.ScanResultsRequestBody(purl)
        val response = dosService.getScanResults(requestBody)

        return if (response.isSuccessful) {
            if (response.body()?.results.isNullOrBlank()) {
                logger.info { "No earlier scan results at DOS API for package URL: $purl" }
                null
            } else {
                logger.info { "Scan results from API: ${response.body()?.results}" }
                response.body()?.results
            }
        } else {
            logger.error { "$response" }
            null
        }
    }

    /**
     * Send info to API about a new zipped package awaiting in S3 to scan.
     * Response: (unzipped) folder name at S3.
     */
    suspend fun getScanFolder(zipFile: String): String? {
        if (zipFile.isEmpty()) {
            logger.error { "Need the name of the zipped file awaiting in S3" }
            return null
        }
        val requestBody = DOSService.PackageRequestBody(zipFile)
        val response = dosService.getScanFolder(requestBody)

        return if (response.isSuccessful) {
            if (response.body()?.folderName.isNullOrBlank()) {
                logger.error { "Error in getting the folder at S3 to scan from DOS API" }
                null
            } else {
                logger.info { "Folder to scan at S3: ${response.body()?.folderName}" }
                response.body()?.folderName
            }
        } else {
            logger.error { "$response" }
            null
        }
    }

    suspend fun postScanJob(scanFolder: String): DOSService.ScanResponseBody? {
        if (scanFolder.isEmpty()) {
            logger.error { "Empty folder given for scan request" }
            return null
        }
        val requestBody = DOSService.ScanRequestBody(scanFolder)
        val response = dosService.postScanJob(requestBody)

        return if (response.isSuccessful) {
            response.body()
        } else {
            logger.error { "$response" }
            null
        }
    }

    suspend fun getJobState(id: String): String? {
        val response = dosService.getJobState(id)

        return if (response.isSuccessful) {
            response.body()?.state
        } else {
            logger.error { "$response" }
            null
        }

    }
}
