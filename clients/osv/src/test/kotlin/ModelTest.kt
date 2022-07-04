/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

package org.ossreviewtoolkit.clients.osv

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import java.io.File

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class ModelTest : StringSpec({
    "Deserializing and serializing any vulnerability is idempotent for all official examples" {
        getVulnerabilityExamplesJson().forAll { vulnerabilityJson ->
            val vulnerability = JSON.decodeFromString<Vulnerability>(vulnerabilityJson)

            val serializedVulnerabilityJson = JSON.encodeToString(vulnerability)

            normalizeJson(serializedVulnerabilityJson) shouldBe normalizeJson(vulnerabilityJson)
        }
    }
})

private val JSON = Json {}

private fun getVulnerabilityExamplesJson(): List<String> =
    (1..7).map { i -> File("src/test/assets/vulnerability/examples/$i.json").readText() }

private fun normalizeJson(value: String): String {
    val json = Json { prettyPrint = true }
    val rootNode = json.decodeFromString<JsonElement>(value)

    return json.encodeToString(rootNode.sortProperties())
}

private fun JsonElement.sortProperties(): JsonElement =
    when (this) {
        is JsonObject -> JsonObject(
            entries.map { it.key to it.value.sortProperties() }.sortedBy { it.first }.toMap()
        )
        else -> this
    }
