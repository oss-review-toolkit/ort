/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.nexusiq

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import java.net.URI
import java.util.UUID

import okhttp3.Credentials
import okhttp3.OkHttpClient

import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Interface for the NexusIQ REST API, based on the documentation from
 * https://help.sonatype.com/iqserver/automating/rest-apis.
 */
interface NexusIqService {
    companion object {
        /**
         * The mapper for JSON (de-)serialization used by this service.
         */
        val JSON_MAPPER = JsonMapper().registerKotlinModule()

        /**
         * Create a NexusIQ service instance for communicating with a server running at the given [url],
         * optionally using a pre-built OkHttp [client].
         */
        fun create(
            url: String,
            user: String? = null,
            password: String? = null,
            client: OkHttpClient? = null
        ): NexusIqService {
            val nexusIqClient = (client ?: OkHttpClient()).newBuilder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val token = UUID.randomUUID().toString()
                    val requestBuilder = request.newBuilder()
                        .header("X-CSRF-TOKEN", token)
                        .header("Cookie", "CLM-CSRF-TOKEN=$token")

                    if (user != null && password != null) {
                        requestBuilder.header("Authorization", Credentials.basic(user, password))
                    }

                    chain.proceed(requestBuilder.build())
                }
                .build()

            val retrofit = Retrofit.Builder()
                .client(nexusIqClient)
                .baseUrl(url)
                .addConverterFactory(JacksonConverterFactory.create(JSON_MAPPER))
                .build()

            return retrofit.create(NexusIqService::class.java)
        }
    }

    data class ComponentDetailsWrapper(
        val componentDetails: List<ComponentDetails>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ComponentDetails(
        val component: Component,
        val securityData: SecurityData
    )

    data class SecurityData(
        val securityIssues: List<SecurityIssue>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SecurityIssue(
        val reference: String,
        val severity: Float,
        val url: URI?
    )

    data class ComponentsWrapper(
        val components: List<Component>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Component(
        val packageUrl: String
    )

    /**
     * Retrieve the details for multiple [components].
     * See https://help.sonatype.com/iqserver/automating/rest-apis/component-details-rest-api---v2.
     */
    @POST("api/v2/components/details")
    suspend fun getComponentDetails(@Body components: ComponentsWrapper): ComponentDetailsWrapper
}
