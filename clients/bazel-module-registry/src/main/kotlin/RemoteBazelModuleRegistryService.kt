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

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

import org.apache.logging.log4j.kotlin.logger

import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The client uses the Bazel Central Registry by default.
 */
const val DEFAULT_URL = "https://bcr.bazel.build"

/**
 * A remote Bazel Module Registry service based on the directory structure of https://bcr.bazel.build/ and the Git
 * repository it is based on (https://github.com/bazelbuild/bazel-central-registry/). It uses [client] to send HTTP
 * request to the registry at [baseUrl].
 */
class RemoteBazelModuleRegistryService(
    private val client: RetrofitRegistryServiceClient,
    baseUrl: String
) : BazelModuleRegistryService {
    companion object {
        /**
         * Create a Bazel Module Registry client instance for communicating with a server running at the given [url],
         * defaulting to the Bazel Central Registry, optionally with a pre-built OkHttp [client].
         */
        fun create(url: String? = null, client: OkHttpClient? = null): RemoteBazelModuleRegistryService {
            val bmrClient = client ?: OkHttpClient()

            val contentType = "application/json".toMediaType()
            val baseUrl = url ?: DEFAULT_URL

            logger.info { "Creating remote Bazel module registry at $baseUrl." }

            val retrofit = Retrofit.Builder()
                .client(bmrClient)
                .baseUrl(baseUrl)
                .addConverterFactory(JSON.asConverterFactory(contentType))
                .build()

            val retrofitClient = retrofit.create(RetrofitRegistryServiceClient::class.java)
            return RemoteBazelModuleRegistryService(retrofitClient, baseUrl)
        }
    }

    override val urls: List<String> = listOf(baseUrl)

    override suspend fun getModuleMetadata(name: String): ModuleMetadata = client.getModuleMetadata(name)

    override suspend fun getModuleSourceInfo(name: String, version: String): ModuleSourceInfo =
        client.getModuleSourceInfo(name, version)
}
