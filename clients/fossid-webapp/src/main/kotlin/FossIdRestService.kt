/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.clients.fossid

import java.util.concurrent.TimeUnit

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.fossid.model.CreateScanResponse
import org.ossreviewtoolkit.clients.fossid.model.Project
import org.ossreviewtoolkit.clients.fossid.model.RemoveUploadContentResponse
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.result.FossIdScanResult
import org.ossreviewtoolkit.clients.fossid.model.result.MatchedLines
import org.ossreviewtoolkit.clients.fossid.model.result.Snippet
import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanDescription
import org.ossreviewtoolkit.clients.fossid.model.status.ScanDescription2021dot2

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule

private const val READ_TIMEOUT_HEADER = "READ_TIMEOUT"

interface FossIdRestService {
    companion object {
        /**
         * The mapper for JSON (de-)serialization used by this service.
         */
        val JSON_MAPPER: ObjectMapper = jsonMapper {
            propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // FossID has a bug in get_results/scan.
            // Sometimes the match_type is "ignored", sometimes it is "Ignored".
            enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            addModule(
                kotlinModule()
                    .addDeserializer(PolymorphicList::class.java, PolymorphicListDeserializer())
                    .addDeserializer(PolymorphicInt::class.java, PolymorphicIntDeserializer())
                    .addDeserializer(PolymorphicData::class.java, PolymorphicDataDeserializer())
            )
        }

        /**
         * Create the [FossIdServiceWithVersion] to interact with the FossID instance running at the given [url],
         * optionally using a pre-built OkHttp [client].
         */
        suspend fun create(
            url: String,
            client: OkHttpClient? = null,
            logRequests: Boolean = false
        ): FossIdServiceWithVersion {
            logger.info { "The FossID server URL is $url." }

            val builder = client?.newBuilder() ?: OkHttpClient.Builder()
            if (logRequests) {
                builder.addInterceptor(LoggingInterceptor)
            }

            val retrofit = Retrofit.Builder()
                .client(builder.addInterceptor(TimeoutInterceptor).build())
                .baseUrl(url)
                .addConverterFactory(JacksonConverterFactory.create(JSON_MAPPER))
                .build()

            val service = retrofit.create(FossIdRestService::class.java)

            return FossIdServiceWithVersion.create(service).also {
                if (it.version.isEmpty()) {
                    logger.warn { "The FossID server is running an unknown version." }
                } else {
                    logger.info { "The FossID server is running version ${it.version}." }
                }
            }
        }
    }

    @POST("api.php")
    suspend fun getProject(@Body body: PostRequestBody): PolymorphicDataResponseBody<Project>

    @POST("api.php")
    suspend fun listProjects(@Body body: PostRequestBody): PolymorphicResponseBody<Project>

    @POST("api.php")
    suspend fun getScan(@Body body: PostRequestBody): PolymorphicDataResponseBody<Scan>

    @POST("api.php")
    suspend fun listScansForProject(@Body body: PostRequestBody): PolymorphicResponseBody<Scan>

    @POST("api.php")
    suspend fun createProject(@Body body: PostRequestBody): MapResponseBody<String>

    @POST("api.php")
    suspend fun createScan(@Body body: PostRequestBody): PolymorphicDataResponseBody<CreateScanResponse>

    @POST("api.php")
    suspend fun runScan(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    @Headers("$READ_TIMEOUT_HEADER:${5 * 60 * 1000}")
    suspend fun deleteScan(@Body body: PostRequestBody): EntityResponseBody<PolymorphicInt>

    @POST("api.php")
    suspend fun downloadFromGit(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun checkDownloadStatus(@Body body: PostRequestBody): PolymorphicDataResponseBody<DownloadStatus>

    @POST("api.php")
    suspend fun checkScanStatus(@Body body: PostRequestBody): EntityResponseBody<ScanDescription>

    @POST("api.php")
    suspend fun checkScanStatus2021dot2(@Body body: PostRequestBody): EntityResponseBody<ScanDescription2021dot2>

    @POST("api.php")
    suspend fun listScanResults(@Body body: PostRequestBody): PolymorphicResponseBody<FossIdScanResult>

    @POST("api.php")
    suspend fun listSnippets(@Body body: PostRequestBody): PolymorphicResponseBody<Snippet>

    @POST("api.php")
    @Headers("$READ_TIMEOUT_HEADER:${5 * 60 * 1000}")
    suspend fun listMatchedLines(@Body body: PostRequestBody): PolymorphicDataResponseBody<MatchedLines>

    @POST("api.php")
    @Headers("$READ_TIMEOUT_HEADER:${60 * 1000}")
    suspend fun listIdentifiedFiles(@Body body: PostRequestBody): PolymorphicResponseBody<IdentifiedFile>

    @POST("api.php")
    suspend fun listMarkedAsIdentifiedFiles(
        @Body body: PostRequestBody
    ): PolymorphicResponseBody<MarkedAsIdentifiedFile>

    @POST("api.php")
    suspend fun listIgnoredFiles(@Body body: PostRequestBody): PolymorphicResponseBody<IgnoredFile>

    @POST("api.php")
    suspend fun listPendingFiles(@Body body: PostRequestBody): PolymorphicResponseBody<String>

    @POST("api.php")
    suspend fun listIgnoreRules(@Body body: PostRequestBody): PolymorphicResponseBody<IgnoreRule>

    @POST("api.php")
    suspend fun createIgnoreRule(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    @Headers("$READ_TIMEOUT_HEADER:${5 * 60 * 1000}")
    suspend fun generateReport(@Body body: PostRequestBody): Response<ResponseBody>

    @POST("api.php")
    suspend fun markAsIdentified(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun unmarkAsIdentified(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun addLicenseIdentification(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun addComponentIdentification(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun addFileComment(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun extractArchives(@Body body: PostRequestBody): EntityResponseBody<Boolean>

    @POST("api.php")
    @Headers("$READ_TIMEOUT_HEADER:${60 * 1000}")
    suspend fun removeUploadedContent(
        @Body body: PostRequestBody
    ): PolymorphicDataResponseBody<RemoveUploadContentResponse>

    @POST("api.php")
    @Headers("Content-Type: application/octet-stream")
    suspend fun uploadFile(
        @Header("FOSSID-SCAN-CODE") scanCodeB64: String,
        @Header("FOSSID-FILE-NAME") fileNameB64: String,
        @Header("Authorization") authorization: String,
        @Header("Transfer-Encoding") transferEncoding: String,
        @Body payload: RequestBody
    ): EntityResponseBody<Nothing>

    @GET("index.php?form=login")
    suspend fun getLoginPage(): ResponseBody
}

/**
 * An interceptor to set the timeout of the requests based on the call headers. If no timeout header is present,
 * default timeout of the client is used.
 */
private object TimeoutInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val newReadTimeout = request.header(READ_TIMEOUT_HEADER)

        val readTimeout = newReadTimeout?.toIntOrNull() ?: chain.readTimeoutMillis()

        return chain.withReadTimeout(readTimeout, TimeUnit.MILLISECONDS).proceed(request)
    }
}
