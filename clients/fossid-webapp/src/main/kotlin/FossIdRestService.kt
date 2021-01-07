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

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import okhttp3.OkHttpClient
import okhttp3.ResponseBody

import org.ossreviewtoolkit.clients.fossid.model.Project
import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.result.FossIdScanResult
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanStatus

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface FossIdRestService {
    companion object {
        val JSON_MAPPER = JsonMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
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
    fun getProject(@Body body: PostRequestBody): Call<EntityPostResponseBody<Project>>

    @POST("api.php")
    fun listScansForProject(@Body body: PostRequestBody): Call<EntityPostResponseBody<Any>>

    @POST("api.php")
    fun createProject(@Body body: PostRequestBody): Call<MapResponseBody<String>>

    @POST("api.php")
    fun createScan(@Body body: PostRequestBody): Call<MapResponseBody<String>>

    @POST("api.php")
    fun runScan(@Body body: PostRequestBody): Call<EntityPostResponseBody<Nothing>>

    @POST("api.php")
    fun downloadFromGit(@Body body: PostRequestBody): Call<EntityPostResponseBody<Nothing>>

    @POST("api.php")
    fun checkDownloadStatus(@Body body: PostRequestBody): Call<EntityPostResponseBody<DownloadStatus>>

    @POST("api.php")
    fun checkScanStatus(@Body body: PostRequestBody): Call<EntityPostResponseBody<ScanStatus>>

    @POST("api.php")
    fun listScanResults(@Body body: PostRequestBody): Call<MapResponseBody<FossIdScanResult>>

    @POST("api.php")
    fun listIdentifiedFiles(@Body body: PostRequestBody): Call<MapResponseBody<IdentifiedFile>>

    @POST("api.php")
    fun listMarkedAsIdentifiedFiles(@Body body: PostRequestBody): Call<EntityPostResponseBody<Any>>

    @POST("api.php")
    fun listIgnoredFiles(@Body body: PostRequestBody): Call<EntityPostResponseBody<Any>>

    @GET("index.php?form=login")
    fun getLoginPage(): Call<ResponseBody>
}
