/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.clients.opensourceinsights

import com.squareup.wire.GrpcClient

import deps_dev.v3.GetVersionRequest
import deps_dev.v3.InsightsClient
import deps_dev.v3.System as PackageSystem
import deps_dev.v3.Version
import deps_dev.v3.VersionKey

import okhttp3.OkHttpClient
import okhttp3.Protocol

const val DEFAULT_URL = "https://api.deps.dev"

fun createInsightsClient(serverUrl: String = DEFAULT_URL, httpClient: OkHttpClient? = null): InsightsClient {
    val client = httpClient ?: OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .build()

    val grpcClient = GrpcClient.Builder()
        .client(client)
        .baseUrl(serverUrl)
        .build()

    return grpcClient.create(InsightsClient::class)
}

/**
 * A service for the Open Source Insights API. The [client] uses gRPC natively to communicate with the server. The
 * documentation links, however, refer to the REST API (JSON over HTTP) which is generated from the gRPC interface.
 */
class OpenSourceInsightsService(private val client: InsightsClient) {
    constructor() : this(createInsightsClient())

    // See https://docs.deps.dev/api/v3/#getversion.
    suspend fun getVersion(system: PackageSystem, name: String, version: String): Version {
        val request = GetVersionRequest(
            VersionKey(
                system = system,
                name = name,
                version = version
            )
        )

        return client.GetVersion().execute(request)
    }
}
