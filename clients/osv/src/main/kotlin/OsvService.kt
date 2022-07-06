/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

import okhttp3.OkHttpClient

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * This class wraps the OSV Rest API client in order to make its use simpler and less error-prone.
 */
class OsvService(serverUrl: String = OsvApiClient.SERVER_URL_PRODUCTION, httpClient: OkHttpClient? = null) {
    private val client = OsvApiClient.create(serverUrl, httpClient)

    /**
     * Get the vulnerabilities for the package matching the given [request].
     */
    fun getVulnerabilitiesForPackage(
        request: VulnerabilitiesForPackageRequest
    ): Result<List<Vulnerability>> {
        val response = client.getVulnerabilitiesForPackage(request).execute()

        return when (response.isSuccessful) {
            true -> Result.success(response.body()!!.vulnerabilities)
            else -> Result.failure(IOException(response.message()))
        }
    }

    /**
     * Return the vulnerability IDs for the respective package matched by the given [requests].
     */
    fun getVulnerabilityIdsForPackages(
        requests: List<VulnerabilitiesForPackageRequest>
    ): Result<List<List<String>>> {
        if (requests.isEmpty()) return Result.success(emptyList())

        val result = mutableListOf<MutableList<String>>()

        requests.chunked(OsvApiClient.BATCH_REQUEST_MAX_SIZE).forEach { requestsChunk ->
            val batchRequest = VulnerabilitiesForPackageBatchRequest(requestsChunk)
            val response = client.getVulnerabilityIdsForPackages(batchRequest).execute()

            if (!response.isSuccessful) return Result.failure(IOException(response.message()))

            result += response.body()!!.results.map { batchResponse ->
                batchResponse.vulnerabilities.mapTo(mutableListOf()) { it.id }
            }
        }

        return Result.success(result)
    }

    /**
     * Return the vulnerability denoted by the given [id].
     */
    fun getVulnerabilityForId(id: String): Result<Vulnerability> {
        val response = client.getVulnerabilityForId(id).execute()

        return when (response.isSuccessful) {
            true -> Result.success(response.body()!!)
            else -> Result.failure(IOException(response.message()))
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
        val result = ConcurrentLinkedQueue<Vulnerability>()
        val failureThrowable = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(ids.size)

        ids.forEach { id ->
            client.getVulnerabilityForId(id).enqueue(object : Callback<Vulnerability> {
                override fun onResponse(call: Call<Vulnerability>, response: Response<Vulnerability>) {
                    response.body()?.let { result += it }
                    latch.countDown()
                }

                override fun onFailure(call: Call<Vulnerability>, t: Throwable) {
                    failureThrowable.set(t)
                    latch.countDown()
                }
            })
        }

        latch.await()

        return failureThrowable.get()?.let { Result.failure(it) }
            ?: Result.success(result.toList())
    }
}
