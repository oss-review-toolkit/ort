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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

import okhttp3.OkHttpClient

import org.apache.logging.log4j.kotlin.logger

import retrofit2.HttpException

/**
 * This class wraps the OSV service to make its use simpler and less error-prone.
 */
class OsvServiceWrapper(
    serverUrl: String? = null,
    httpClient: OkHttpClient? = null,
    private val service: OsvService = OsvService.create(serverUrl, httpClient)
) {
    // The outcome of looking up a single package: either the vulnerability IDs on success, or a failure message
    // describing why the request was rejected by OSV.
    sealed class RequestResult {
        data class Success(val ids: List<String>) : RequestResult()
        data class Failure(val message: String) : RequestResult()
    }

    /**
     * Return the vulnerability IDs for the respective package matched by the given [requests]. The result has the
     * same order as [requests]; each entry is either a [RequestResult.Success] with the vulnerability IDs, or a
     * [RequestResult.Failure] describing why the request was rejected by OSV.
     *
     * The OSV batch endpoint is all-or-nothing per chunk, so when a chunk's batch request fails the failing
     * request(s) are isolated when the error contains a parseable failing index (and the chunk is retried with them
     * excluded). If the error has no parseable index (e.g. transport errors or 5xx), the whole batch is reported as
     * failed by throwing an [IOException], which callers should treat as a provider-level failure.
     */
    fun getVulnerabilityIdsForPackages(requests: List<VulnerabilitiesForPackageRequest>): List<RequestResult> {
        if (requests.isEmpty()) return emptyList()

        @Suppress("ForbiddenMethodCall")
        val perChunkResults: List<List<RequestResult>> =
            runBlocking(Dispatchers.IO.limitedParallelism(20)) {
                coroutineScope {
                    requests.chunked(OsvService.BATCH_REQUEST_MAX_SIZE)
                        .map { requestsChunk ->
                            async { getVulnerabilityIdsForChunk(requestsChunk) }
                        }.awaitAll()
                }
            }

        val flatResults = perChunkResults.flatten()
        val failureCount = flatResults.count { it is RequestResult.Failure }
        if (failureCount > 0) {
            logger.info {
                "OSV: finished batch vulnerability lookup, $failureCount of ${flatResults.size} request(s) failed."
            }
        }

        return flatResults
    }

    private suspend fun getVulnerabilityIdsForChunk(
        requestsChunk: List<VulnerabilitiesForPackageRequest>
    ): List<RequestResult> {
        val batchResult = sendBatchRequest(requestsChunk)

        if (batchResult.isSuccess) {
            return batchResult.getOrThrow().map { RequestResult.Success(it) }
        }

        val initialError = batchResult.exceptionOrNull()?.message ?: "unknown error"
        val reportedIndex = parseFailingIndexFromError(initialError)

        if (reportedIndex == null || reportedIndex !in requestsChunk.indices) {
            // The error has no parseable index, so we can't isolate a specific bad request. This typically indicates
            // a general OSV failure (server down, 5xx, etc.) rather than a bad request. Surface it as a provider-level
            // failure so the caller reports it once instead of duplicating it per-package.
            logger.warn {
                "Batch request for ${requestsChunk.size} package(s) failed with no parseable failing index " +
                    "($initialError); failing the entire chunk."
            }

            throw IOException(initialError)
        }

        // OSV reported a specific bad index; isolate it and recurse on both halves in parallel.
        logger.warn {
            "Batch request for ${requestsChunk.size} package(s) failed ($initialError), " +
                "isolating the failing request(s)."
        }

        val failingIndices = isolateFailingRequests(requestsChunk)
        val retryChunk = requestsChunk.filterIndexed { index, _ -> index !in failingIndices }
        val retryResults: List<RequestResult> = if (retryChunk.isNotEmpty()) {
            val retryResult = sendBatchRequest(retryChunk)
            if (retryResult.isSuccess) {
                retryResult.getOrThrow().map { RequestResult.Success(it) }
            } else {
                // The retry failed even though the bad requests have been excluded; this likely indicates a
                // transient error (e.g. OSV unreachable). Surface it as a provider-level failure so the caller
                // can restart the scan.
                logger.error {
                    "Retried batch request for ${retryChunk.size} package(s) still failed: " +
                        "${retryResult.exceptionOrNull()?.message}"
                }

                throw IOException(retryResult.exceptionOrNull()?.message ?: "Retry of batch failed.")
            }
        } else {
            emptyList()
        }

        val results = ArrayList<RequestResult>(requestsChunk.size)
        var retryIndex = 0
        requestsChunk.indices.forEach { index ->
            results += if (index in failingIndices) {
                RequestResult.Failure(
                    "Request was rejected by the OSV batch endpoint: $initialError"
                )
            } else {
                retryResults[retryIndex++]
            }
        }

        return results
    }

    /**
     * Identify the indices of [requestsChunk] that cause the batch request to fail. Splits at the index OSV reports
     * in the error message and recurses on both halves in parallel to find any additional bad requests. Returns the
     * set of all bad indices relative to [requestsChunk].
     */
    private suspend fun isolateFailingRequests(requestsChunk: List<VulnerabilitiesForPackageRequest>): Set<Int> {
        val result = sendBatchRequest(requestsChunk)
        if (result.isSuccess) return emptySet()

        val errorMessage = result.exceptionOrNull()?.message ?: return setOf(0)
        val reportedIndex = parseFailingIndexFromError(errorMessage)
            ?.takeIf { it in requestsChunk.indices }

        if (reportedIndex == null) return setOf(0)

        val (leftFailures, rightFailures) = coroutineScope {
            val leftDeferred = async { isolateFailingRequests(requestsChunk.subList(0, reportedIndex)) }
            val rightDeferred = async {
                isolateFailingRequests(requestsChunk.subList(reportedIndex + 1, requestsChunk.size))
                    .map { it + reportedIndex + 1 }
                    .toSet()
            }

            leftDeferred.await() to rightDeferred.await()
        }

        return leftFailures + rightFailures + reportedIndex
    }

    /**
     * Parse the failing request index from an OSV batch error message, e.g. for "Error code 3: error in query at
     * index 57: ..." returns 57. Returns null if the message does not match the expected shape.
     */
    private fun parseFailingIndexFromError(message: String): Int? {
        val match = failingIndexRegex.find(message) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private suspend fun sendBatchRequest(
        requestsChunk: List<VulnerabilitiesForPackageRequest>
    ): Result<List<List<String>>> =
        runCatching {
            val batchRequest = VulnerabilitiesForPackageBatchRequest(requestsChunk)
            service.getVulnerabilityIdsForPackages(batchRequest)
        }.map { batchResponse ->
            batchResponse.results.map { idList ->
                idList.vulnerabilities.mapTo(mutableListOf()) { it.id }
            }
        }.handleHttpException()

    /**
     * Return the vulnerabilities for the given [ids], in the iteration order of [ids]. If any lookup fails, the
     * returned [Result] is a failure.
     */
    fun getVulnerabilitiesForIds(ids: Set<String>): Result<List<Vulnerability>> =
        @Suppress("ForbiddenMethodCall")
        runBlocking(Dispatchers.IO.limitedParallelism(20)) {
            runCatching {
                ids.map { id ->
                    async { service.getVulnerabilityForId(id) }
                }.awaitAll()
            }.handleHttpException()
        }
}

private fun <T> Result<T>.handleHttpException() =
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

private val failingIndexRegex = Regex("""error in query at index (\d+)""")
