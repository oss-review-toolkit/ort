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

package org.ossreviewtoolkit.clients.dos
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.RequestBody
import org.apache.logging.log4j.kotlin.Logging
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url
import retrofit2.Response

interface DOSService {

    companion object: Logging {
        /**
         * The default API URL.
         */
        private val DEFAULT_API_URL = System.getenv("DOS_URL")

        /**
         * The JSON (de-)serialization object used by this service.
         */
        private val JSON = Json {
            ignoreUnknownKeys = true
        }

        /**
         * Create a new service instance that connects to the [url] specified and uses the optionally provided [client].
         */
        fun create(url: String? = null, client: OkHttpClient? = null): DOSService {
            val contentType = "application/json; charset=utf-8".toMediaType()

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                // For logging basic call -> response statuses, use BASIC
                // For logging the request and response bodies of a call, use BODY
                level = HttpLoggingInterceptor.Level.NONE
            }

            val okHttpClient = client ?: OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()

            val retrofit = Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(url ?: DEFAULT_API_URL)
                .addConverterFactory(JSON.asConverterFactory(contentType))
                .build()

            return retrofit.create(DOSService::class.java)
        }
    }

    @Serializable
    data class PresignedUrlRequestBody(
        val key: String? = null
    )

    @Serializable
    data class PresignedUrlResponseBody(
        val success: Boolean,
        val presignedUrl: String? = null,
        val message: String? = null
    )

    @Serializable
    data class ScanResultsRequestBody(
        val purl: String? = null
    )

    @Serializable
    data class ScanResultsResponseBody(
        val results: String? = null
    )

    @PUT
    suspend fun putS3File(@Url url: String, @Body file: RequestBody): Response<Unit>

    @POST("upload-url")
    suspend fun getPresignedUrl(@Body body: PresignedUrlRequestBody): PresignedUrlResponseBody

    @POST("scan-results")
    suspend fun getScanResults(@Body body: ScanResultsRequestBody): ScanResultsResponseBody
}
