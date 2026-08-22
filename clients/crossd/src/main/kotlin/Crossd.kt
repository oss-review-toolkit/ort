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

package org.ossreviewtoolkit.clients.crossd

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

suspend fun HttpClient.getAvailableSnapshots(packageId: String): List<Double> {
    val response = post("/api/snapshots") {
        setBody(
            buildJsonObject {
                put("term", packageId)
            }
        )
    }

    if (!response.status.isSuccess()) {
        return emptyList()
    }

    return (response.body() as JsonArray).map { it.jsonPrimitive.double }.sortedDescending()
}

suspend fun HttpClient.getLatestSnapshot(packageId: String) = getAvailableSnapshots(packageId).getOrNull(0)

suspend fun HttpClient.getAverageValues(): Map<String, Double> {
    val response = post("/api/metrics/avg")
    if (!response.status.isSuccess()) {
        return emptyMap()
    }

    val averages = (response.body() as JsonObject)["avg"]?.jsonObject
        ?: return emptyMap()

    return averages.mapValues {
        it.value.jsonPrimitive.double
    }
}

suspend fun HttpClient.getMetrics(packageId: String, snapshot: Double): JsonObject {
    val response = post("/api/metrics") {
        setBody(
            buildJsonObject {
                put("term", packageId)
                put("timestamp", snapshot)
            }
        )
    }

    if (!response.status.isSuccess()) {
        return JsonObject(emptyMap())
    }

    val metrics = (response.body() as JsonObject)["metrics"]?.jsonObject
        ?: return JsonObject(emptyMap())

    return metrics
}

suspend fun HttpClient.getMetrics(packageId: String): JsonObject =
    getLatestSnapshot(packageId)?.let {
        getMetrics(packageId, it)
    } ?: JsonObject(emptyMap())
