/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import java.time.Duration

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor

import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Url

/**
 * The service for the REST API client of the Double Open Server (DOS), see https://github.com/doubleopen-project/dos/.
 */
interface DosService {
    companion object {
        /**
         * Create a new service instance that connects to the specified DOS [url] with an authorization [token] and
         * uses the optionally provided [timeout] value and HTTP [client].
         */
        fun create(url: String, token: String, timeout: Duration? = null, client: OkHttpClient? = null): DosService {
            val contentType = "application/json; charset=utf-8".toMediaType()

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                // For logging basic call-response statuses, use "BASIC".
                // For logging the request and response bodies of a call, use "BODY".
                level = HttpLoggingInterceptor.Level.NONE
            }

            val authorizationInterceptor = AuthorizationInterceptor(token)

            val okHttpClient = client ?: OkHttpClient.Builder()
                .apply {
                    if (timeout != null) {
                        callTimeout(timeout)
                        connectTimeout(timeout)
                        readTimeout(timeout)
                        writeTimeout(timeout)
                    }
                }
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authorizationInterceptor)
                .build()

            val retrofit = Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(url)
                .addConverterFactory(JSON.asConverterFactory(contentType))
                .build()

            return retrofit.create(DosService::class.java)
        }
    }

    /**
     * Get the package configuration for specified [purl][PackageConfigurationRequestBody.purl].
     */
    @POST("package-configuration")
    suspend fun getPackageConfiguration(@Body body: PackageConfigurationRequestBody): PackageConfigurationResponseBody

    /**
     * Get the pre-signed upload URL for S3 object storage with the specified object [key][UploadUrlRequestBody.key].
     */
    @POST("upload-url")
    suspend fun getUploadUrl(@Body body: UploadUrlRequestBody): UploadUrlResponseBody

    /**
     * Put the [file] into the S3 object storage at [url].
     */
    @SkipAuthorization
    @PUT
    suspend fun uploadFile(@Url url: String, @Body file: RequestBody)

    /**
     * Add new scanner job for specified [packages][JobRequestBody.packages]. In case multiple packages are provided, it
     * is assumed that they all refer to the same provenance (like a monorepo).
     */
    @POST("job")
    suspend fun addScanJob(@Body body: JobRequestBody): JobResponseBody

    /**
     * Get scan results for specified [packages][ScanResultsRequestBody.packages]. In case multiple packages are
     * provided, it is assumed that they all refer to the same provenance (like a monorepo).
     */
    @POST("scan-results")
    suspend fun getScanResults(@Body body: ScanResultsRequestBody): ScanResultsResponseBody

    /**
     * Get the state for scan job with given [id].
     */
    @GET("job-state/{id}")
    suspend fun getScanJobState(@Path("id") id: String): JobStateResponseBody
}
