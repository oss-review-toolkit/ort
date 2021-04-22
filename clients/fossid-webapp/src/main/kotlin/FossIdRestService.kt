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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.clients.fossid

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import okhttp3.OkHttpClient
import okhttp3.ResponseBody

import org.ossreviewtoolkit.clients.fossid.model.Project
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanStatus

import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface FossIdRestService {
    companion object {
        /**
         * The mapper for JSON (de-)serialization used by this service.
         */
        val JSON_MAPPER = JsonMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // FossID has a bug in get_results/scan.
            // Sometimes the match_type is "ignored", sometimes it is "Ignored".
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .registerKotlinModule()

        /**
         * Create a FossID service instance for communicating with a server running at the given [url],
         * optionally using a pre-built OkHttp [client].
         */
        fun create(url: String, client: OkHttpClient? = null): FossIdRestService {
            val retrofit = Retrofit.Builder()
                .apply { if (client != null) client(client) }
                .baseUrl(url)
                .addConverterFactory(JacksonConverterFactory.create(JSON_MAPPER))
                .build()

            return retrofit.create(FossIdRestService::class.java)
        }
    }

    @POST("api.php")
    suspend fun getProject(@Body body: PostRequestBody): EntityResponseBody<Project>

    @POST("api.php")
    suspend fun listScansForProject(@Body body: PostRequestBody): EntityResponseBody<Any>

    @POST("api.php")
    suspend fun createProject(@Body body: PostRequestBody): MapResponseBody<String>

    @POST("api.php")
    suspend fun createScan(@Body body: PostRequestBody): MapResponseBody<String>

    @POST("api.php")
    suspend fun runScan(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun deleteScan(@Body body: PostRequestBody): EntityResponseBody<Int>

    @POST("api.php")
    suspend fun downloadFromGit(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun checkDownloadStatus(@Body body: PostRequestBody): EntityResponseBody<DownloadStatus>

    @POST("api.php")
    suspend fun checkScanStatus(@Body body: PostRequestBody): EntityResponseBody<ScanStatus>

    @POST("api.php")
    suspend fun listScanResults(@Body body: PostRequestBody): EntityResponseBody<Any>

    @POST("api.php")
    suspend fun listIdentifiedFiles(@Body body: PostRequestBody): EntityResponseBody<Any>

    @POST("api.php")
    suspend fun listMarkedAsIdentifiedFiles(@Body body: PostRequestBody): EntityResponseBody<Any>

    @POST("api.php")
    suspend fun listIgnoredFiles(@Body body: PostRequestBody): EntityResponseBody<Any>

    @POST("api.php")
    suspend fun listPendingFiles(@Body body: PostRequestBody): EntityResponseBody<Any>

    @GET("index.php?form=login")
    suspend fun getLoginPage(): ResponseBody
}
