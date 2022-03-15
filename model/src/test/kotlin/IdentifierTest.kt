/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldNotStartWith
import io.kotest.matchers.string.shouldStartWith

import org.ossreviewtoolkit.model.utils.createPurl
import org.ossreviewtoolkit.model.utils.toPurl

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

        "be sorted as expected" {
            val ids = listOf(
                Identifier("Maven:org.springframework.boot:spring-boot-actuator"),
                Identifier("Maven:org.springframework.boot:spring-boot")
            )

            ids.sorted() should containExactly(
                Identifier("Maven:org.springframework.boot:spring-boot"),
                Identifier("Maven:org.springframework.boot:spring-boot-actuator")
            )
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

            map should containExactly(Identifier("type", "namespace", "name", "version") to 1)
        }

        "be deserialized correctly from a map key even if incomplete" {
            val serializedMap = "---\ntype:namespace:: 1"

            val map = yamlMapper.readValue<Map<Identifier, Int>>(serializedMap)

            map should containExactly(Identifier("type", "namespace", "", "") to 1)
        }
    }

    "Purl representations" should {
        "not suffix the scheme with '//'" {
            val purl = Identifier("type", "namespace", "name", "version").toPurl()

            purl shouldStartWith "pkg:"
            purl shouldNotStartWith "pkg://"
        }

        "not percent-encode the type" {
            val purl = Identifier("azAZ09.+-", "namespace", "name", "version").toPurl()

            purl shouldNotContain "%"
        }

        "ignore case in type" {
            val purl = Identifier("MaVeN", "namespace", "name", "version").toPurl()

            purl shouldBe purl.lowercase()
        }

        "use given type if it is not a known package manager" {
            val purl = Identifier("FooBar", "namespace", "name", "version").toPurl()

            purl shouldStartWith "pkg:foobar"
        }

        "not use '/' for empty namespaces" {
            val purl = Identifier("type", "", "name", "version").toPurl()

            purl shouldBe "pkg:type/name@version"
        }

        "percent-encode namespaces with segments" {
            val purl = Identifier("type", "name/space", "name", "version").toPurl()

            purl shouldBe "pkg:type/name%2Fspace/name@version"
        }

        "percent-encode the name" {
            val purl = Identifier("type", "namespace", "fancy name", "version").toPurl()

            purl shouldBe "pkg:type/namespace/fancy%20name@version"
        }

        "percent-encode the version" {
            val purl = Identifier("type", "namespace", "name", "release candidate").toPurl()

            purl shouldBe "pkg:type/namespace/name@release%20candidate"
        }

        "allow qualifiers" {
            val purl = createPurl(
                "type",
                "namespace",
                "name",
                "version",
                mapOf("argName" to "argValue")
            )

            purl shouldBe "pkg:type/namespace/name@version?argName=argValue"
        }

        "allow multiple qualifiers" {
            val purl = createPurl(
                "type",
                "namespace",
                "name",
                "version",
                mapOf("argName1" to "argValue1", "argName2" to "argValue2")
            )

            purl shouldBe "pkg:type/namespace/name@version?argName1=argValue1&argName2=argValue2"
        }

        "allow subpath" {
            val purl = createPurl(
                "type",
                "namespace",
                "name",
                "version",
                subpath = "value1/value2"
            )

            purl shouldBe "pkg:type/namespace/name@version#value1/value2"
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
