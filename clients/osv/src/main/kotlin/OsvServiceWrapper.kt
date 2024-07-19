/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

import okhttp3.OkHttpClient

/**
 * This class wraps the OSV service to make its use simpler and less error-prone.
 */
class OsvServiceWrapper(serverUrl: String? = null, httpClient: OkHttpClient? = null) {
    private val service = OsvService.create(serverUrl, httpClient)

    /**
     * Return the vulnerability IDs for the respective package matched by the given [requests].
     */
    suspend fun getVulnerabilityIdsForPackages(requests: List<VulnerabilitiesForPackageRequest>): Result<List<List<String>>> =
        withContext(Dispatchers.IO) {
            if (requests.isEmpty()) return@withContext Result.success(emptyList())

            val result = mutableListOf<MutableList<String>>()

            requests.chunked(OsvService.BATCH_REQUEST_MAX_SIZE).forEach { requestsChunk ->
                val batchRequest = VulnerabilitiesForPackageBatchRequest(requestsChunk)
                val response = service.getVulnerabilityIdsForPackages(batchRequest)
                val body = response.body()

                if (!response.isSuccessful || body == null) {
                    val errorMessage = response.errorBody()?.string()?.let {
                        val errorResponse = OsvService.JSON.decodeFromString<ErrorResponse>(it)
                        "Error code ${errorResponse.code}: ${errorResponse.message}"
                    } ?: with(response) { "HTTP code ${code()}: ${message()}" }

                    return@withContext Result.failure(IOException(errorMessage))
                }

                result += body.results.map { batchResponse ->
                    batchResponse.vulnerabilities.mapTo(mutableListOf()) { it.id }
                }
            }

            Result.success(result)
        }

    /**
     * Return the vulnerabilities denoted by the given [ids].
     *
     * This executes a separate request for each given identifier since a batch request is not available.
     * It's been considered to add a batch API in the future, see
     * https://github.com/google/osv.dev/issues/466#issuecomment-1163337495.
     */
    suspend fun getVulnerabilitiesForIds(ids: Set<String>): Result<List<Vulnerability>> =
        withContext(Dispatchers.IO) {
            val result = ConcurrentLinkedQueue<Vulnerability>()
            val failureThrowable = AtomicReference<Throwable?>(null)

            ids.map { async { it to service.getVulnerabilityForId(it) } }
                .map { deferred ->
                    val (id, response) = deferred.await()
                    when {
                        response.isSuccessful -> response.body()?.let { result += it }
                        else -> {
                            failureThrowable.set(
                                IOException("Could not get vulnerability information for '$id': ${response.message()}")
                            )
                        }
                    }
                }

            failureThrowable.get()?.let { Result.failure(it) }
                ?: Result.success(result.toList())
        }
}
