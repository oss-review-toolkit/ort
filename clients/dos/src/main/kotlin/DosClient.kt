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

package org.ossreviewtoolkit.clients.dos

import java.io.File

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.utils.common.formatSizeInMib

/**
 * A client implementation on top of the DOS service.
 */
class DosClient(private val service: DosService) {
    /**
     * Get the package configuration for the given [purl]. Return a [PackageConfigurationResponseBody] or null on error.
     */
    suspend fun getPackageConfiguration(purl: String): PackageConfigurationResponseBody? {
        if (purl.isEmpty()) {
            logger.error { "The Package URL is required to get the package configuration." }
            return null
        }

        val requestBody = PackageConfigurationRequestBody(purl)
        val response = service.getPackageConfiguration(requestBody)
        val responseBody = response.body()

        return if (response.isSuccessful && responseBody != null) {
            responseBody
        } else {
            logger.error { "Error getting the package configuration for PURL $purl: ${response.errorBody()}" }

            null
        }
    }

    /**
     * Return a pre-signed URL as provided by the DOS API to upload a package to S3 object storage for scanning, or null
     * if the request did not succeed.
     */
    suspend fun getUploadUrl(key: String): String? {
        if (key.isEmpty()) {
            logger.error { "Need the name of the zipped packet to upload" }
            return null
        }

        val requestBody = UploadUrlRequestBody(key)
        val response = service.getUploadUrl(requestBody)
        val responseBody = response.body()

        return if (response.isSuccessful && responseBody != null && responseBody.success) {
            response.body()?.presignedUrl
        } else {
            logger.error { "Unable to get a pre-signed URL for $key: ${response.errorBody()?.string()}" }
            null
        }
    }

    /**
     * Upload the file at [file] to S3 using the [pre-signed URL][presignedUrl] and return true on success or false
     * other on failure.
     */
    suspend fun uploadFile(file: File, presignedUrl: String): Boolean {
        logger.info { "Uploading file $file of size ${file.formatSizeInMib} to S3..." }

        val contentType = "application/zip".toMediaType()
        val response = service.uploadFile(presignedUrl, file.asRequestBody(contentType))

        return if (response.isSuccessful) {
            logger.info { "Successfully uploaded $file to S3." }
            true
        } else {
            logger.error { "Failed to upload $file to S3: ${response.errorBody()}" }
            false
        }
    }

    /**
     * Add a new scan job [zipFileKey] and [purls]. Return a [JobResponseBody] or null on error.
     */
    suspend fun addScanJob(zipFileKey: String, purls: List<String>): JobResponseBody? {
        if (zipFileKey.isEmpty() || purls.isEmpty()) {
            logger.error { "The ZIP file key and Package URLs are required to add a scan job." }
            return null
        }

        val requestBody = JobRequestBody(zipFileKey, purls)
        val response = service.addScanJob(requestBody)
        val responseBody = response.body()

        return if (response.isSuccessful && responseBody != null) {
            responseBody
        } else {
            logger.error { "Error adding a new scan job for $zipFileKey and $purls: ${response.errorBody()}" }

            null
        }
    }

    /**
     * Get scan results for a list of [purls]. In case multiple purls are provided, it is assumed that they all refer to
     * the same provenance (like a monorepo). If [fetchConcluded] is true, return concluded licenses instead of detected
     * licenses. Return either existing results, a "pending" message if the package is currently being scanned, a
     * "no-results" message if a scan yielded no results, or null on error.
     */
    suspend fun getScanResults(purls: List<String>, fetchConcluded: Boolean): ScanResultsResponseBody? {
        if (purls.isEmpty()) {
            logger.error { "The list of PURLs to get scan results for must not be empty." }
            return null
        }

        val options = ScanResultsRequestBody.ReqOptions(fetchConcluded)
        val requestBody = ScanResultsRequestBody(purls, options)
        val response = service.getScanResults(requestBody)
        val responseBody = response.body()

        return if (response.isSuccessful && responseBody != null) {
            when (responseBody.state.status) {
                "no-results" -> logger.info { "No scan results found for $purls." }
                "pending" -> logger.info { "Scan pending for $purls." }
                "ready" -> {
                    logger.info { "Scan results ready for $purls." }

                    if (fetchConcluded) {
                        logger.info { "Returning concluded licenses instead of detected licenses." }
                    }
                }
            }

            responseBody
        } else {
            logger.error { "Error getting scan results: ${response.errorBody()}" }

            null
        }
    }

    /**
     * Get the state of the scan job with the given [id]. Return a [JobStateResponseBody] with one of waiting / active /
     * completed / failed state, or null on error.
     */
    suspend fun getScanJobState(id: String): JobStateResponseBody? {
        if (id.isEmpty()) {
            logger.error { "Need the job ID to check for job state" }
            return null
        }

        val response = service.getScanJobState(id)
        val responseBody = response.body()

        return if (response.isSuccessful && responseBody != null) {
            responseBody
        } else {
            logger.error { "Error getting the scan state for job $id: ${response.errorBody()}" }

            null
        }
    }
}
