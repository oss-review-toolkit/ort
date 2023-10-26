/*
 * SPDX-FileCopyrightText: 2023 HH Partners
 *
 * SPDX-License-Identifier: MIT
 */

package org.ossreviewtoolkit.clients.dos

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.ossreviewtoolkit.clients.dos.DOSService.Companion.logger
import java.io.File
import java.io.FileInputStream

/**
 * This class implements the data layer of the DOS client.
 */
class DOSRepository(private val dosService: DOSService) {
    /**
     * Request a presigned URL from DOS API to upload a package to S3 Object Storage for scanning.
     * Response: the URL string or null if the request didn't succeed.
     */
    suspend fun getPresignedUrl(key: String): String? {
        if (key.isEmpty()) {
            logger.error { "Need the name of the zipped packet to upload" }
            return null
        }
        val requestBody = DOSService.UploadUrlRequestBody(key)
        val response = dosService.postUploadUrl(requestBody)

        return if (response.isSuccessful) {
            if (response.body()?.success == true) {
                logger.info { "Presigned URL from API successfully fetched" }
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
     * Response: true/false indication of the success of operation.
     */
    suspend fun uploadFile(presignedUrl: String, filePath: String): Boolean {
        val file = File(filePath)
        val fileSize = (file.length() / 1024).toString().toInt()
        logger.info("Uploading file $filePath of size $fileSize kB to S3")

        val mediaType = "application/zip".toMediaType()
        val maxSizeForDirectRequestBody = 512 * 1024 // 512 MB
        val requestBody = if (fileSize > maxSizeForDirectRequestBody) {
            logger.info { "Big file: using buffered copy" }
            createRequestBody(file, mediaType)
        } else {
            file.readBytes().toRequestBody(mediaType)
        }

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
     * Response:
     * - if earlier results are found, return them in a JSON String
     * - if no earlier results found, but the package is currently
     *   being scanned, return "pending" message
     * - otherwise, return "null"
     */
    suspend fun getScanResults(purl: String, fetchConcluded: Boolean): DOSService.ScanResultsResponseBody? {
        if (purl.isEmpty()) {
            logger.error { "Need the package URL to check for scan results" }
            return null
        }
        val options = DOSService.ScanResultsRequestBody.ReqOptions(fetchConcluded)
        val requestBody = DOSService.ScanResultsRequestBody(purl, options)
        val response = dosService.postScanResults(requestBody)

        return if (response.isSuccessful) {
            when (response.body()?.state?.status) {
                "no-results" -> logger.info { "No scan results found from DOS API for $purl" }
                "pending" -> logger.info { "Pending scan for $purl" }
                "ready" -> {
                    logger.info { "Scan results found from DOS API for $purl" }
                    if (fetchConcluded) {
                        logger.info { "Using license conclusions instead of detected licenses" }
                    }
                }
            }
            response.body()
        } else {
            logger.error { "$response" }
            null
        }
    }

    /**
     * Post a new scan job to DOS API for [zipFileKey] and [purl].
     */
    suspend fun postScanJob(zipFileKey: String, purl: String): DOSService.JobResponseBody? {
        if (zipFileKey.isEmpty() || purl.isEmpty()) {
            logger.error { "Need the Zip filename and package URL to send the scan job" }
            return null
        }
        val requestBody = DOSService.JobRequestBody(zipFileKey, purl)
        val response = dosService.postJob(requestBody)

        return if (response.isSuccessful) {
            response.body()
        } else {
            logger.error { "$response" }
            null
        }
    }

    /**
     * Request job status from DOS API.
     * Response: waiting / active / completed / failed.
     */
    suspend fun getJobState(id: String): DOSService.JobStateResponseBody? {
        if (id.isEmpty()) {
            logger.error { "Need the job ID to check for job state" }
            return null
        }
        val response = dosService.getJobState(id)

        return if (response.isSuccessful) {
            response.body()
        } else {
            logger.error { "$response" }
            null
        }
    }

    /**
     * Request package configuration from DOS API.
     */
    suspend fun postPackageConfiguration(purl: String): DOSService.PackageConfigurationResponseBody? {
        if (purl.isEmpty()) {
            logger.error { "Need the package URL to check for package configuration" }
            return null
        }
        val requestBody = DOSService.PackageConfigurationRequestBody(purl)
        val response = dosService.postPackageConfiguration(requestBody)

        return if (response.isSuccessful) {
            response.body()
        } else {
            logger.error { "$response" }
            null
        }
    }

    /**
     * Handle requests for very large packages, like pkg:npm/%40fontsource/open-sans@5.0.12
     * by using a buffered copy of the file instead of a direct copy, in order to avoid
     * OutOfMemoryError.
     */
    private fun createRequestBody(file: File, mediaType: MediaType): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType = mediaType
            override fun contentLength(): Long = file.length()

            override fun writeTo(sink: BufferedSink) {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val inputStream = FileInputStream(file)
                try {
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        sink.write(buffer, 0, bytesRead)
                    }
                } finally {
                    inputStream.close()
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 2048
    }
}
