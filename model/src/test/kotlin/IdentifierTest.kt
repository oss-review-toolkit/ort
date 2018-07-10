/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class IdentifierTest : StringSpec() {
    init {
        "String representation is correct" {
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

            mapping.forEach { identifier, stringRepresentation ->
                identifier.toString() shouldBe stringRepresentation
            }
        }

        "String representation is parsed correctly" {
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

            mapping.forEach { stringRepresentation, identifier ->
                Identifier.fromString(stringRepresentation) shouldBe identifier
            }
        }

        "Identifiers match correctly" {
            val matching = mapOf(
                    "maven:org.hamcrest:hamcrest-core:1.3"
                            to "maven:org.hamcrest:hamcrest-core:1.3",
                    "maven:org.hamcrest:hamcrest-core:1.3"
                            to "maven:org.hamcrest:hamcrest-core:",
                    "maven:org.hamcrest:hamcrest-core:1.3"
                            to "maven:org.hamcrest::1.3",
                    "maven:org.hamcrest:hamcrest-core:1.3"
                            to "maven:org.hamcrest::")

            val nonMatching = mapOf(
                    "maven:org.hamcrest:hamcrest-core:1.3"
                            to "maven:org.hamcrest:hamcrest-core:1.2",
                    "maven:org.hamcrest:hamcrest-core:1.3"
                            to "maven:org.hamcrest:hamcrest-library:",
                    "maven:org.hamcrest:hamcrest-core:"
                            to "maven:org.hamcrest:hamcrest-library:",
                    "maven:org.hamcrest::"
                            to "maven:org.apache::",
                    "maven:org.hamcrest::"
                            to "gradle:org.hamcrest::"
            )

            matching.forEach { id1, id2 ->
                Identifier.fromString(id1).matches(Identifier.fromString(id2)) shouldBe true
            }

            nonMatching.forEach { id1, id2 ->
                Identifier.fromString(id1).matches(Identifier.fromString(id2)) shouldBe false
            }
        }
    }
}
