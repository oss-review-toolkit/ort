/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

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

            mapping.entries.forAll { (identifier, stringRepresentation) ->
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

            mapping.entries.forAll { (stringRepresentation, identifier) ->
                Identifier(stringRepresentation) shouldBe identifier
            }
        }

        "be sorted as expected".config(invocations = 5) {
            val sorted = listOf(
                Identifier("Maven:com.microsoft.sqlserver:mssql-jdbc:9.2.1.jre8"),
                Identifier("Maven:com.microsoft.sqlserver:mssql-jdbc:9.2.1.jre11"),
                Identifier("Maven:net.java.dev.jna:jna-platform:5.6.0"),
                Identifier("Maven:net.java.dev.jna:jna-platform:5.11.0"),
                Identifier("Maven:net.java.dev.jna:jna-platform:NOT_A_VERSION"),
                Identifier("Maven:org.springframework.boot:spring-boot"),
                Identifier("Maven:org.springframework.boot:spring-boot-actuator")
            )
            val unsorted = sorted.shuffled()

            unsorted.sorted() shouldBe sorted
        }

        "be serialized correctly" {
            val id = Identifier("type", "namespace", "name", "version")

            val serializedId = id.toYaml()

            serializedId shouldBe "--- \"type:namespace:name:version\"\n"
        }

        "be deserialized correctly" {
            val serializedId = "--- \"type:namespace:name:version\""

            val id = serializedId.fromYaml<Identifier>()

            id shouldBe Identifier("type", "namespace", "name", "version")
        }

        "be deserialized correctly even if incomplete" {
            val serializedId = "--- \"type:namespace:\""

            val id = serializedId.fromYaml<Identifier>()

            id shouldBe Identifier("type", "namespace", "", "")
        }

        "be deserialized correctly from a map key" {
            val serializedMap = "---\ntype:namespace:name:version: 1"

            val map = serializedMap.fromYaml<Map<Identifier, Int>>()

            map should containExactly(Identifier("type", "namespace", "name", "version") to 1)
        }

        "be deserialized correctly from a map key even if incomplete" {
            val serializedMap = "---\ntype:namespace:: 1"

            val map = serializedMap.fromYaml<Map<Identifier, Int>>()

            map should containExactly(Identifier("type", "namespace", "", "") to 1)
        }
    }

    "Checking the organization" should {
        "work as expected" {
            assertSoftly {
                Identifier("Maven:org.ossreviewtoolkit:name:version")
                    .isFromOrg("ossreviewtoolkit", "foobar") shouldBe true
                Identifier("Maven:org.ossreviewtoolkit.project:name:version")
                    .isFromOrg("ossreviewtoolkit") shouldBe true
                Identifier("Maven:org.apache:name:version").isFromOrg("apache") shouldBe true
                Identifier("NPM:@scope:name:version").isFromOrg("scope") shouldBe true
                Identifier("Maven:example:name:version").isFromOrg("example") shouldBe true

                Identifier("").isFromOrg("ossreviewtoolkit") shouldBe false
                Identifier("type:namespace:name:version").isFromOrg("ossreviewtoolkit") shouldBe false
                Identifier("Maven:project.com.here:name:version").isFromOrg("ossreviewtoolkit") shouldBe false
            }
        }
    }
})
