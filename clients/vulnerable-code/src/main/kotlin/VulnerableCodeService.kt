/*
 * Copyright (C) 2021 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import okhttp3.OkHttpClient

import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Interface for a REST service that allows interaction with the VulnerableCode API to query information about
 * vulnerabilities detected for specific packages.
 */
interface VulnerableCodeService {
    companion object {
        /**
         * The mapper for JSON (de-)serialization used by this service.
         */
        val JSON_MAPPER = JsonMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .registerKotlinModule()

        /**
         * Create a new service instance that connects to the [serverUrl] specified and uses the optionally provided
         * [client].
         */
        fun create(serverUrl: String, client: OkHttpClient? = null): VulnerableCodeService {
            val retrofit = Retrofit.Builder()
                .apply { if (client != null) client(client) }
                .baseUrl(serverUrl)
                .addConverterFactory(JacksonConverterFactory.create(JSON_MAPPER))
                .build()

            return retrofit.create(VulnerableCodeService::class.java)
        }
    }

    /**
     * Data class to represent a reference to a vulnerability. When querying the vulnerabilities for packages, each
     * package in the result has a list of this type. In order to retrieve further details about vulnerabilities,
     * an additional request has to performed.
     */
    data class HyperLinkedVulnerability(
        /** A URL with information about the vulnerability. */
        val url: String,

        /**
         * The ID of this vulnerability, typically a CVE identifier. According to the VulnerableCode schema, this can
         * be *null*.
         */
        val vulnerabilityId: String?
    )

    /**
     * Data class representing a vulnerability. Objects of this type are in the result of a query for vulnerabilities.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Vulnerability(
        /** A URL with information about the vulnerability. */
        val url: String,

        /**
         * The identifier for this vulnerability. According to the VulnerableCode schema, this can be *null*.
         */
        val cveId: String?,

        /**
         * The vulnerability score. According to the VulnerableCode schema, this can be *null*.
         */
        val cvss: Float?
    )

    /**
     * Data class describing a package in the result of a package query. An instance contains information about
     * the vulnerabilities that have been found for this package.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Vulnerabilities(
        /** An optional list with vulnerabilities that have not yet been resolved. */
        val unresolvedVulnerabilities: List<HyperLinkedVulnerability> = emptyList(),

        /** An optional list with vulnerabilities that have already been resolved. */
        val resolvedVulnerabilities: List<HyperLinkedVulnerability> = emptyList()
    )

    /**
     * Data class to represent the bulk request for vulnerabilities by their IDs. The request body is a JSON object
     * with a property containing a list of vulnerability IDs.
     */
    data class VulnerabilitiesWrapper(
        val vulnerabilities: List<String>
    )

    /**
     * Data class to represent the bulk request for packages by their IDs. Using this request, the vulnerabilities
     * known for a set of packages can be retrieved. The request body is a JSON object with a property containing a
     * list of package identifiers.
     */
    data class PackagesWrapper(
        val packages: List<String>
    )

    /**
     * Retrieve information about the vulnerabilities assigned to the given [packages][packageUrls].
     * Return a map whose keys are the URLs of packages; so for each package the referenced vulnerabilities can be
     * retrieved.
     */
    @POST("api/packages/bulk_search")
    suspend fun getPackageVulnerabilities(@Body packageUrls: PackagesWrapper): Map<String, Vulnerabilities>

    /**
     * Retrieve detail information about the specified [vulnerabilities][vulnerabilityIds]. The resulting map
     * associates the IDs of vulnerabilities with their details.
     */
    @POST("api/vulnerabilities/bulk_search")
    suspend fun getVulnerabilityDetails(@Body vulnerabilityIds: VulnerabilitiesWrapper): Map<String, Vulnerability>

    /**
     * Retrieve detail information for the vulnerability with the given [id].
     */
    @GET("api/vulnerabilities/{id}")
    suspend fun getVulnerability(@Path("id") id: String): Vulnerability
}
