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

import io.ks3.java.typealiases.InstantAsString

import java.util.concurrent.Executors

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * An interface for the REST API of the Google Open Source Vulnerabilities (OSV) service, see https://osv.dev/.
 */
interface OsvService {
    companion object {
        const val BATCH_REQUEST_MAX_SIZE = 1000

        /** The URL of the production server. */
        const val PRODUCTION_SERVER_URL = "https://api.osv.dev"

        /** The URL of the staging server. */
        const val STAGING_SERVER_URL = "https://api-staging.osv.dev"

        val JSON = Json { namingStrategy = JsonNamingStrategy.SnakeCase }

        fun create(serverUrl: String? = null, client: OkHttpClient? = null): OsvService {
            val converterFactory = JSON.asConverterFactory(contentType = "application/json".toMediaType())

            return Retrofit.Builder()
                .apply { client(client ?: defaultHttpClient()) }
                .baseUrl(serverUrl ?: PRODUCTION_SERVER_URL)
                .addConverterFactory(converterFactory)
                .build()
                .create(OsvService::class.java)
        }
    }

    /**
     * Get the identifiers of the vulnerabilities for the packages matched by the respective given [request].
     * The amount of requests contained in the give [batch request][request] must not exceed [BATCH_REQUEST_MAX_SIZE].
     */
    @POST("v1/querybatch")
    suspend fun getVulnerabilityIdsForPackages(
        @Body request: VulnerabilitiesForPackageBatchRequest
    ): VulnerabilitiesForPackageBatchResponse

    /**
     * Return the vulnerability denoted by the given [id].
     */
    @GET("v1/vulns/{id}")
    suspend fun getVulnerabilityForId(@Path("id") id: String): Vulnerability
}

@Serializable
class VulnerabilitiesForPackageRequest(
    val commit: String? = null,
    @SerialName("package")
    val pkg: Package? = null,
    val version: String? = null
)

@Serializable
data class VulnerabilitiesForPackageBatchRequest(
    val queries: List<VulnerabilitiesForPackageRequest>
)

@Serializable
data class VulnerabilitiesForPackageBatchResponse(
    val results: List<IdList>
) {
    @Serializable
    data class IdList(
        @SerialName("vulns")
        val vulnerabilities: List<Id> = emptyList()
    )

    @Serializable
    data class Id(
        val id: String,
        val modified: InstantAsString
    )
}

private fun defaultHttpClient(): OkHttpClient {
    // Experimentally determined value to speed-up execution time of 1000 single vulnerability-by-id requests.
    val n = 100
    val dispatcher = Dispatcher(Executors.newFixedThreadPool(n)).apply {
        maxRequests = n
        maxRequestsPerHost = n
    }

    return OkHttpClient.Builder().dispatcher(dispatcher).build()
}
