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

        return if (!response.success) {
            logger.error { "Failed to get presigned URL from S3: ${response.message}" }
            null
        } else {
            logger.info { "Presigned URL from API: $response.presignedUrl" }
            response.presignedUrl
        }
    }

    /**
     * Upload a file to S3, using presigned URL.
     */
    suspend fun uploadFile(presignedUrl: String, filePath: String): Boolean {
        val file = File(filePath)
        val requestBody = file.readBytes().toRequestBody("application/zip".toMediaType())
        val response = dosService.putS3File(presignedUrl, requestBody)

        return if (!response.isSuccessful) {
            logger.error { "Failed to upload packet to S3: ${response.message()}" }
            false
        } else {
            logger.info { "Packet successfully uploaded to S3" }
            true
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

        return if (response.results.isNullOrBlank()) {
            logger.info { "No earlier scan results for package URL: $purl" }
            null
        } else {
            logger.info { "Scan results from API: ${response.results}" }
            response.results
        }
    }

    /**
     * Send info to API about a new zipped package awaiting in S3 to scan.
     * Response: (unzipped) folder name at S3.
     */
    suspend fun getScanFolder(zipFile: String?): String? {
        val requestBody = DOSService.PackageRequestBody(zipFile)
        val response = dosService.getScanFolder(requestBody).folderName

        return response
    }

    suspend fun postScanJob(scanFolder: String?): DOSService.ScanResponseBody {
        val requestBody = DOSService.ScanRequestBody(scanFolder)
        val response = dosService.postScanJob(requestBody)

        return response
    }

    suspend fun getJobState(id: String): String {
        val response = dosService.getJobState(id)

        return response.state
    }
}
