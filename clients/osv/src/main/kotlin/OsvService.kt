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
 * The list of data sources is documented at https://google.github.io/osv.dev/data/#current-data-sources.
 */
interface OsvService {
    companion object {
        const val BATCH_REQUEST_MAX_SIZE = 1000

        /** The URL of the production server. */
        const val PRODUCTION_SERVER_URL = "https://api.osv.dev"

        /** The URL of the staging server. */
        const val STAGING_SERVER_URL = "https://api-staging.osv.dev"

        val JSON = Json {
            namingStrategy = JsonNamingStrategy.SnakeCase
            ignoreUnknownKeys = true
        }

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

    /** Return the vulnerability denoted by the given [id]. */
    @GET("v1/vulns/{id}")
    suspend fun getVulnerabilityForId(@Path("id") id: String): Vulnerability
}

/** See https://google.github.io/osv.dev/post-v1-query/#parameters. */
@Serializable
class VulnerabilitiesForPackageRequest(
    /** The commit hash to query for. If specified, [version] should not be set. */
    val commit: String? = null,

    /**
     * The version string to query for. A fuzzy match is done against upstream versions. If set, [commit] must not be
     * used, and [Package.purl] must not include a version.
     */
    val version: String? = null,

    /** The package to query against. When a commit hash is given, this is optional. */
    @SerialName("package")
    val pkg: Package? = null
)

/** See https://google.github.io/osv.dev/post-v1-querybatch/#parameters. */
@Serializable
data class VulnerabilitiesForPackageBatchRequest(
    /** Each query item must follow the same rules as [VulnerabilitiesForPackageRequest]. */
    val queries: List<VulnerabilitiesForPackageRequest>
)

/** See https://google.github.io/osv.dev/post-v1-querybatch/#sample-200-response. */
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
        /** The data-source-specific ID of the vulnerability. */
        val id: String,

        /** The date when the vulnerability was last modified. */
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
