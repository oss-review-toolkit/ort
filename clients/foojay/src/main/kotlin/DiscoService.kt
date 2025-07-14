/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.foojay

import java.util.EnumSet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * A minimalistic service interface for Foojay's Disco API, see https://api.foojay.io/swagger-ui.
 */
interface DiscoService {
    companion object {
        const val DEFAULT_SERVER_URL = "https://api.foojay.io/disco/"

        val JSON = Json {
            ignoreUnknownKeys = true
            namingStrategy = JsonNamingStrategy.SnakeCase
        }

        fun create(url: String? = null, client: OkHttpClient? = null): DiscoService {
            val converterFactory = JSON.asConverterFactory(contentType = "application/json".toMediaType())
            val retrofit = Retrofit.Builder()
                .apply { if (client != null) client(client) }
                .baseUrl(url ?: DEFAULT_SERVER_URL)
                .addConverterFactory(converterFactory)
                .build()

            return retrofit.create(DiscoService::class.java)
        }
    }

    /**
     * See https://api.foojay.io/swagger-ui#/default/getPackagesV3.
     */
    @Suppress("LongParameterList")
    @GET("v3.0/packages")
    suspend fun getPackages(
        @Query("version") version: String,
        @Query("distribution") distributions: EnumSet<Distribution>,
        @Query("architecture") architectures: EnumSet<Architecture>,
        @Query("archive_type") archiveTypes: EnumSet<ArchiveType>,
        @Query("package_type") packageTypes: EnumSet<PackageType>,
        @Query("operating_system") operatingSystems: EnumSet<OperatingSystem>,
        @Query("lib_c_type") libCTypes: EnumSet<LibCType>,
        @Query("release_status") releaseStatuses: EnumSet<ReleaseStatus>,
        @Query("directly_downloadable") directlyDownloadable: Boolean,
        @Query("latest") latest: Latest,
        @Query("free_to_use_in_production") freeToUseInProduction: Boolean
    ): PackagesResult
}
