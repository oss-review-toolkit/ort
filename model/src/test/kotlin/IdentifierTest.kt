/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.model

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotlintest.assertSoftly
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class IdentifierTest : WordSpec({
    "String representations" should {
        "be correct" {
            val mapping = mapOf(
                Identifier("manager", "namespace", "name", "version")
                        to "manager:namespace:name:version",
                Identifier("", "", "", "")
                        to ":::",
                Identifier("manager", "namespace", "name", "")
                        to "manager:namespace:name:",
                Identifier("manager", "", "name", "version")
                        to "manager::name:version"
            )

            mapping.forEach { (identifier, stringRepresentation) ->
                identifier.toCoordinates() shouldBe stringRepresentation
            }
        }

        "be parsed correctly" {
            val mapping = mapOf(
                "manager:namespace:name:version"
                        to Identifier("manager", "namespace", "name", "version"),
                ":::"
                        to Identifier("", "", "", ""),
                "manager:namespace:name:"
                        to Identifier("manager", "namespace", "name", ""),
                "manager::name:version"
                        to Identifier("manager", "", "name", "version")
            )

            mapping.forEach { (stringRepresentation, identifier) ->
                Identifier(stringRepresentation) shouldBe identifier
            }
        }

        "be serialized correctly" {
            val id = Identifier("type", "namespace", "name", "version")

            val serializedId = yamlMapper.writeValueAsString(id)

            serializedId shouldBe "--- \"type:namespace:name:version\"\n"
        }

        "be deserialized correctly" {
            val serializedId = "--- \"type:namespace:name:version\""

            val id = yamlMapper.readValue<Identifier>(serializedId)

            id shouldBe Identifier("type", "namespace", "name", "version")
        }

        "be deserialized correctly even if incomplete" {
            val serializedId = "--- \"type:namespace:\""

            val id = yamlMapper.readValue<Identifier>(serializedId)

            id shouldBe Identifier("type", "namespace", "", "")
        }

        "be deserialized correctly from a map key" {
            val serializedMap = "---\ntype:namespace:name:version: 1"

            val map = yamlMapper.readValue<Map<Identifier, Int>>(serializedMap)

            map shouldBe mapOf(Identifier("type", "namespace", "name", "version") to 1)
        }

        "be deserialized correctly from a map key even if incomplete" {
            val serializedMap = "---\ntype:namespace:: 1"

            val map = yamlMapper.readValue<Map<Identifier, Int>>(serializedMap)

            map shouldBe mapOf(Identifier("type", "namespace", "", "") to 1)
        }
    }

    "Checking the organization" should {
        "work as expected" {
            assertSoftly {
                Identifier("Maven:com.here:name:version").isFromOrg("here", "traffic") shouldBe true
                Identifier("Maven:com.here.project:name:version").isFromOrg("here") shouldBe true
                Identifier("Maven:org.apache:name:version").isFromOrg("apache") shouldBe true
                Identifier("NPM:@scope:name:version").isFromOrg("scope") shouldBe true

                Identifier("").isFromOrg("here") shouldBe false
                Identifier("type:namespace:name:version").isFromOrg("here") shouldBe false
                Identifier("Maven:project.com.here:name:version").isFromOrg("here") shouldBe false
            }
        }
    }
})
