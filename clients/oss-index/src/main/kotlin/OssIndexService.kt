/*
 * Copyright (C) 2021 Sonatype
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

package org.ossreviewtoolkit.clients.ossindex

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import okhttp3.Credentials
import okhttp3.OkHttpClient

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Interface for the OssIndex REST API, based on the documentation from
 * https://ossindex.sonatype.org/rest.
 */
interface OssIndexService {
    companion object {
        /**
         * Create a OSS Index service instance for communicating with a server running at the given [url],
         * optionally using a pre-built OkHttp [client].
         */
        fun create(
            url: String,
            user: String? = null,
            password: String? = null,
            client: OkHttpClient? = null
        ): OssIndexService {
            val ossIndexClient = (client ?: OkHttpClient()).newBuilder().apply {
                addInterceptor { chain ->
                    val request = chain.request()
                    val requestBuilder = request.newBuilder()
                        .header("User-Agent", "ort-oss-index")

                    if (user != null && password != null) {
                        requestBuilder.header("Authorization", Credentials.basic(user, password))
                    }

                    chain.proceed(requestBuilder.build())
                }
            }.build()

            val retrofit = Retrofit.Builder()
                .client(ossIndexClient)
                .baseUrl(url)
                .addConverterFactory(JacksonConverterFactory.create(JsonMapper().registerKotlinModule()))
                .build()

            return retrofit.create(OssIndexService::class.java)
        }
    }

    data class Component(
        val coordinates: String,
        val description: String,
        val reference: String,
        val vulnerabilities: List<Vulnerability>
    )

    data class Vulnerability(
        val id: String,
        val displayName: String,
        val title: String,
        val description: String,
        val cvssScore: Float,
        val cvssVector: String,
        val cve: String?,
        val cwe: String?,
        val reference: String
    )

    data class ComponentsRequest(
        val coordinates: List<String>
    )

    /**
     * Retrieve the details for multiple [components].
     * See https://ossindex.sonatype.org/rest.
     */
    @POST("api/v3/component-report")
    fun getComponentReport(@Body components: ComponentsRequest): Call<Array<Component>>
}
