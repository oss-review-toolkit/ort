/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.scanoss

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

import kotlinx.serialization.json.Json

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient

import org.ossreviewtoolkit.clients.scanoss.model.ScanResponse

import retrofit2.Retrofit
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

typealias FullScanResponse = Map<String, List<ScanResponse>>

interface ScanOssService {
    companion object {
        /**
         * The JSON (de-)serialization object used by this service.
         */
        val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Create a new service instance that connects to the [url] specified and uses the optionally provided [client].
         */
        fun create(url: String, client: OkHttpClient? = null): ScanOssService {
            val contentType = "application/json".toMediaType()
            val retrofit = Retrofit.Builder()
                .apply { if (client != null) client(client) }
                .baseUrl(url)
                .addConverterFactory(JSON.asConverterFactory(contentType))
                .build()

            return retrofit.create(ScanOssService::class.java)
        }
    }

    /**
     * Scan a file using the SCANOSS streaming API.
     * TODO: Implement support for scanning with SBOM.
     */
    @Multipart
    @POST("scan/direct")
    suspend fun scan(@Part file: MultipartBody.Part): FullScanResponse
}
