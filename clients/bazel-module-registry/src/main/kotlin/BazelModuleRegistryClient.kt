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

package org.ossreviewtoolkit.clients.bazelmoduleregistry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * The JSON (de-)serialization object used by this client.
 */
private val JSON = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/**
 * The client uses the Bazel Central Registry by default.
 */
private const val DEFAULT_URL = "https://bcr.bazel.build"

/**
 * Interface for a Bazel Module Registry, based on the directory structure of https://bcr.bazel.build
 * and the git repository it is based on (https://github.com/bazelbuild/bazel-central-registry/).
 */
interface BazelModuleRegistryClient {
    companion object {
        /**
         * Create a Bazel Module Registry client instance for communicating with a server running at the given [url],
         * defaulting to the Bazel Central Registry, optionally with a pre-built OkHttp [client].
         */
        fun create(url: String? = null, client: OkHttpClient? = null): BazelModuleRegistryClient {
            val bmrClient = client ?: OkHttpClient()

            val contentType = "application/json".toMediaType()
            val retrofit = Retrofit.Builder()
                .client(bmrClient)
                .baseUrl(url ?: DEFAULT_URL)
                .addConverterFactory(JSON.asConverterFactory(contentType))
                .build()

            return retrofit.create(BazelModuleRegistryClient::class.java)
        }
    }

    /**
     * Retrieve the metadata for a module.
     * E.g. https://bcr.bazel.build/modules/glog/metadata.json.
     */
    @GET("modules/{name}/metadata.json")
    suspend fun getModuleMetadata(@Path("name") name: String): ModuleMetadata

    /**
     * Retrieve information about the source code for a specific version of a module.
     * E.g. https://bcr.bazel.build/modules/glog/0.5.0/source.json.
     */
    @GET("modules/{name}/{version}/source.json")
    suspend fun getModuleSourceInfo(@Path("name") name: String, @Path("version") version: String): ModuleSourceInfo
}
