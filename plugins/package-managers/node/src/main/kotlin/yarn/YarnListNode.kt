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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class YarnListNode(
    val name: String,
    val children: List<YarnListNode>? = null,
    val color: String? = null
)

private val JSON = Json { ignoreUnknownKeys = true }

internal fun parseYarnList(json: String): List<YarnListNode> {
    val items = json.byteInputStream().use { inputStream ->
        Json.decodeToSequence<JsonObject>(inputStream)
    }

    val treeNode = items.firstOrNull {
        it["type"]?.jsonPrimitive?.content == "tree"
    } ?: return emptyList()

    val listNode = treeNode["data"]?.jsonObject
        ?.takeIf { it["type"]?.jsonPrimitive?.content == "list" }
        ?: return emptyList()

    return listNode["trees"]?.let {
        JSON.decodeFromString<List<YarnListNode>>(it.toString())
    }.orEmpty()
}
