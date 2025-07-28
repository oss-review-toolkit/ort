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

import kotlin.coroutines.cancellation.CancellationException

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.utils.common.formatSizeInMib

import retrofit2.HttpException

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

        return runCatching {
            val request = PackageConfigurationRequestBody(purl)
            service.getPackageConfiguration(request)
        }.onFailure {
            when (it) {
                is CancellationException -> currentCoroutineContext().ensureActive()
                is HttpException -> logger.error {
                    "Error getting the package configuration for purl $purl: ${it.response()?.errorBody()?.string()}"
                }

                else -> throw it
            }
        }.getOrNull()
    }

    /**
     * Return a pre-signed URL as provided by the DOS API to upload a package to S3 object storage for scanning, or null
     * if the request did not succeed.
     */
    suspend fun getUploadUrl(key: String): String? {
        if (key.isEmpty()) {
            logger.error { "The key for getting the upload URL must be non-empty." }
            return null
        }

        return runCatching {
            val request = UploadUrlRequestBody(key)
            service.getUploadUrl(request)
        }.map {
            it.presignedUrl
        }.onFailure {
            when (it) {
                is CancellationException -> currentCoroutineContext().ensureActive()
                is HttpException -> logger.error {
                    "Unable to get a pre-signed URL for $key: ${it.response()?.errorBody()?.string()}"
                }

                else -> throw it
            }
        }.getOrNull()
    }

    /**
     * Upload the file at [file] to S3 using the [pre-signed URL][presignedUrl] and return true on success or false
     * other on failure.
     */
    suspend fun uploadFile(file: File, presignedUrl: String): Boolean {
        logger.info { "Uploading file $file of size ${file.formatSizeInMib} to S3..." }

        return runCatching {
            val contentType = "application/zip".toMediaType()
            service.uploadFile(presignedUrl, file.asRequestBody(contentType))
        }.onSuccess {
            logger.info { "Successfully uploaded $file to S3." }
        }.onFailure {
            when (it) {
                is CancellationException -> currentCoroutineContext().ensureActive()
                is HttpException -> logger.error {
                    "Failed to upload $file to S3: ${it.response()?.errorBody()?.string()}"
                }

                else -> throw it
            }
        }.isSuccess
    }

    /**
     * Add a new scan job [zipFileKey] and [packages]. Return a [JobResponseBody] or null on error.
     */
    suspend fun addScanJob(zipFileKey: String, packages: List<PackageInfo>): JobResponseBody? {
        if (zipFileKey.isEmpty() || packages.isEmpty()) {
            logger.error { "The ZIP file key and Package URLs are required to add a scan job." }
            return null
        }

        return runCatching {
            val request = JobRequestBody(zipFileKey, packages)
            service.addScanJob(request)
        }.onFailure {
            when (it) {
                is CancellationException -> currentCoroutineContext().ensureActive()
                is HttpException -> logger.error {
                    val purls = packages.map { pkg -> pkg.purl }
                    "Error adding a new scan job for $zipFileKey and $purls: ${it.response()?.errorBody()?.string()}"
                }

                else -> throw it
            }
        }.getOrNull()
    }

    /**
     * Get scan results for a list of [packages]. In case multiple packages are provided, it is assumed that they all
     * refer to the same provenance (like a monorepo). Return either existing results, a "pending" message if the
     * package is currently being scanned, a "no-results" message if a scan yielded no results, or null on error. If
     * only some of the packages exist in DOS database (identified by purl), new bookmarks for the remaining packages
     * are made (hence the need to provide the declared licenses for these packages in this request).
     */
    suspend fun getScanResults(packages: List<PackageInfo>): ScanResultsResponseBody? {
        if (packages.isEmpty()) {
            logger.error { "The list of PURLs to get scan results for must not be empty." }
            return null
        }

        return runCatching {
            val request = ScanResultsRequestBody(packages)
            service.getScanResults(request)
        }.onSuccess { response ->
            val purls = packages.map { it.purl }

            when (response.state.status) {
                "no-results" -> logger.info { "No scan results found for $purls." }
                "pending" -> logger.info { "Scan pending for $purls." }
                "ready" -> logger.info { "Scan results ready for $purls." }
            }
        }.onFailure {
            when (it) {
                is CancellationException -> currentCoroutineContext().ensureActive()
                is HttpException -> logger.error {
                    "Error getting scan results: ${it.response()?.errorBody()?.string()}"
                }

                else -> throw it
            }
        }.getOrNull()
    }

    /**
     * Get the state of the scan job with the given [id]. Return a [JobStateResponseBody] with one of waiting / active /
     * completed / failed state, or null on error.
     */
    suspend fun getScanJobState(id: String): JobStateResponseBody? {
        if (id.isEmpty()) {
            logger.error { "The job ID for getting the scan job state must be non-empty." }
            return null
        }

        return runCatching {
            service.getScanJobState(id)
        }.onFailure {
            when (it) {
                is CancellationException -> currentCoroutineContext().ensureActive()
                is HttpException -> logger.error {
                    "Error getting the scan state for job $id: ${it.response()?.errorBody()?.string()}"
                }

                else -> throw it
            }
        }.getOrNull()
    }
}
