/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.vulnerablecode

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Interface for a REST service that allows interaction with the VulnerableCode API to query information about
 * vulnerabilities detected for specific packages.
 */
interface VulnerableCodeService {
    companion object {
        /**
         * The JSON (de-)serialization object used by this service.
         */
        val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Create a new service instance that connects to the [url] specified and uses the optionally provided [client].
         */
        fun create(url: String, client: OkHttpClient? = null): VulnerableCodeService {
            val contentType = "application/json".toMediaType()
            val retrofit = Retrofit.Builder()
                .apply { if (client != null) client(client) }
                .baseUrl(url)
                .addConverterFactory(JSON.asConverterFactory(contentType))
                .build()

            return retrofit.create(VulnerableCodeService::class.java)
        }
    }

    /**
     * Data class that represents a score assigned to a vulnerability. A source of vulnerability information can
     * provide multiple score values using different scoring systems.
     */
    @Serializable
    data class Score(
        /** The name of the scoring system. */
        @SerialName("scoring_system")
        val scoringSystem: String,

        /**
         * The value in this scoring system. This is a string to support scoring systems that do not use numeric
         * scores, but literals like _LOW_, _MEDIUM_, etc.
         */
        val value: String
    )

    /**
     * Data class representing a reference to detailed information about a vulnerability. Information about a single
     * vulnerability can come from multiple sources; for each of these sources a reference is added to the data.
     */
    @Serializable
    data class VulnerabilityReference(
        /**
         * The URL of this reference. From this URL, it is also possible to identify the source of information.
         */
        val url: String,

        /**
         * A (possibly empty) list with [Score] objects that determine the severity this source assigns to this
         * vulnerability.
         */
        val scores: List<Score>
    )

    /**
     * Data class representing a single vulnerability with its references to detailed information.
     */
    @Serializable
    data class Vulnerability(
        /** A URL with information about the vulnerability. */
        @SerialName("vulnerability_id")
        val vulnerabilityId: String,

        /** A list with [VulnerabilityReference]s pointing to sources of information about this vulnerability. */
        val references: List<VulnerabilityReference>
    )

    /**
     * Data class describing a package in the result of a package query together with the vulnerabilities known for
     * this package.
     */
    @Serializable
    data class PackageVulnerabilities(
        /** The PURL identifying this package. */
        val purl: String,

        /** An optional list with vulnerabilities that have not yet been resolved. */
        @SerialName("unresolved_vulnerabilities")
        val unresolvedVulnerabilities: List<Vulnerability> = emptyList(),

        /** An optional list with vulnerabilities that have already been resolved. */
        @SerialName("resolved_vulnerabilities")
        val resolvedVulnerabilities: List<Vulnerability> = emptyList()
    )

    /**
     * Data class to represent the bulk request for packages by their IDs. Using this request, the vulnerabilities
     * known for a set of packages can be retrieved. The request body is a JSON object with a property containing a
     * list of package identifiers.
     */
    @Serializable
    data class PackagesWrapper(
        val purls: Collection<String>
    )

    /**
     * Retrieve information about the vulnerabilities assigned to the given [packages][packageUrls].
     * Return a list with information about packages including the resolved and unresolved vulnerabilities for these
     * packages.
     */
    @POST("api/packages/bulk_search")
    suspend fun getPackageVulnerabilities(@Body packageUrls: PackagesWrapper): List<PackageVulnerabilities>
}
