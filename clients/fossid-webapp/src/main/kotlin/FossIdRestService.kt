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

import okhttp3.ResponseBody

import org.ossreviewtoolkit.clients.fossid.api.Project
import org.ossreviewtoolkit.clients.fossid.api.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.api.result.FossIdScanResult
import org.ossreviewtoolkit.clients.fossid.api.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.api.status.ScanStatus

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface FossIdRestService {
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
