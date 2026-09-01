/*
 * Copyright (C) 2022 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.clients.osv

import java.io.IOException

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import okhttp3.OkHttpClient

import retrofit2.HttpException

/**
 * This class wraps the OSV service to make its use simpler and less error-prone.
 */
class OsvServiceWrapper(serverUrl: String? = null, httpClient: OkHttpClient? = null) {
    private val service = OsvService.create(serverUrl, httpClient)

    /**
     * Return the vulnerability IDs for the respective package matched by the given [requests].
     */
    fun getVulnerabilityIdsForPackages(requests: List<VulnerabilitiesForPackageRequest>): Result<List<List<String>>> {
        if (requests.isEmpty()) return Result.success(emptyList())

        @Suppress("ForbiddenMethodCall")
        val batchResults = runBlocking(Dispatchers.IO.limitedParallelism(20)) {
            requests.chunked(OsvService.BATCH_REQUEST_MAX_SIZE).map { requestsChunk ->
                async {
                    val batchRequest = VulnerabilitiesForPackageBatchRequest(requestsChunk)
                    runCatching { service.getVulnerabilityIdsForPackages(batchRequest) }.unwrapHttpException()
                }
            }.awaitAll()
        }

        // Combine the individual results of requests into one encapsulating result that is a failure if any of the
        // requests failed.
        return runCatching {
            batchResults.flatMap { batchResult ->
                batchResult.getOrThrow().results.map { idList ->
                    idList.vulnerabilities.map { it.id }
                }
            }
        }
    }

    /**
     * Return the vulnerabilities denoted by the given [ids].
     *
     * This executes a separate request for each given identifier since a batch request is not available.
     * It's been considered to add a batch API in the future, see
     * https://github.com/google/osv.dev/issues/466#issuecomment-1163337495.
     */
    fun getVulnerabilitiesForIds(ids: Set<String>): Result<List<Vulnerability>> {
        @Suppress("ForbiddenMethodCall")
        val vulnerabilityResults = runBlocking(Dispatchers.IO.limitedParallelism(20)) {
            ids.map { id ->
                async {
                    runCatching { service.getVulnerabilityForId(id) }.unwrapHttpException()
                }
            }.awaitAll()
        }

        // Combine the individual results of requests into one encapsulating result that is a failure if any of the
        // requests failed.
        return runCatching {
            vulnerabilityResults.map { it.getOrThrow() }
        }
    }
}

/**
 * Get the inner code and message of an [HttpException], if any, as part of the [Result], and rethrow them as a
 * streamlined message of an [IOException].
 */
private fun <T> Result<T>.unwrapHttpException() =
    recoverCatching { e ->
        if (e is HttpException) {
            val response = e.response()
            if (response != null) {
                val errorMessage = response.errorBody()?.string()?.let {
                    val errorResponse = OsvService.JSON.decodeFromString<ErrorResponse>(it)
                    "Error code ${errorResponse.code}: ${errorResponse.message}"
                } ?: with(response) { "HTTP code ${code()}: ${message()}" }

                throw IOException(errorMessage)
            }
        }

        throw e
    }
