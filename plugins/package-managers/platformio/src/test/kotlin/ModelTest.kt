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

package org.ossreviewtoolkit.plugins.packagemanagers.platformio

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlinx.serialization.json.Json

class ModelTest : WordSpec({
    "parseLibraryManifest()" should {
        "parse the well-known fields of a library.json file" {
            val json = """
                {
                  "name": "ArduinoJson",
                  "version": "6.21.6",
                  "description": "A simple and efficient JSON library for embedded C++.",
                  "homepage": "https://arduinojson.org",
                  "license": "MIT",
                  "repository": {
                    "type": "git",
                    "url": "https://github.com/bblanchon/ArduinoJson.git"
                  },
                  "authors": {
                    "name": "Benoit Blanchon",
                    "url": "https://blog.benoitblanchon.fr"
                  }
                }
            """.trimIndent()

            val parsed = parseLibraryManifest(json)

            parsed.manifest.name shouldBe "ArduinoJson"
            parsed.manifest.version shouldBe "6.21.6"
            parsed.manifest.license shouldBe "MIT"
            parsed.manifest.homepage shouldBe "https://arduinojson.org"
            parsed.manifest.repository?.type shouldBe "git"
            parsed.manifest.repository?.url shouldBe "https://github.com/bblanchon/ArduinoJson.git"
            parsed.authors shouldBe setOf("Benoit Blanchon")
            parsed.dependencies should beEmpty()
        }

        "parse dependencies declared as a map of name to version specification" {
            val json = """
                {
                  "name": "Foo",
                  "dependencies": {
                    "ArduinoJson": "^6.11.0",
                    "adafruit/Adafruit BusIO": "^1.0.0"
                  }
                }
            """.trimIndent()

            val parsed = parseLibraryManifest(json)

            parsed.dependencies should containExactlyInAnyOrder(
                LibraryDependency(owner = null, name = "ArduinoJson"),
                LibraryDependency(owner = "adafruit", name = "Adafruit BusIO")
            )
        }

        "parse dependencies declared as a list of objects" {
            val json = """
                {
                  "name": "Foo",
                  "dependencies": [
                    {
                      "owner": "bblanchon",
                      "name": "ArduinoJson",
                      "version": "^6.11.0"
                    },
                    {
                      "name": "SPI"
                    }
                  ]
                }
            """.trimIndent()

            val parsed = parseLibraryManifest(json)

            parsed.dependencies should containExactlyInAnyOrder(
                LibraryDependency(owner = "bblanchon", name = "ArduinoJson"),
                LibraryDependency(owner = null, name = "SPI")
            )
        }
    }

    "parseLibraryProperties()" should {
        "parse the well-known fields of a library.properties file" {
            val properties = """
                name=Adafruit SSD1306
                version=2.5.17
                author=Adafruit
                maintainer=Adafruit <info@adafruit.com>
                sentence=SSD1306 oled driver library for monochrome 128x64 and 128x32 displays
                paragraph=A longer description of the library.
                url=https://github.com/adafruit/Adafruit_SSD1306
                depends=Adafruit GFX Library
            """.trimIndent()

            val parsed = parseLibraryProperties(properties)

            parsed.manifest.name shouldBe "Adafruit SSD1306"
            parsed.manifest.version shouldBe "2.5.17"
            parsed.manifest.description shouldBe "A longer description of the library."
            parsed.manifest.homepage shouldBe "https://github.com/adafruit/Adafruit_SSD1306"
            parsed.manifest.repository?.type shouldBe "git"
            parsed.manifest.repository?.url shouldBe "https://github.com/adafruit/Adafruit_SSD1306"
            parsed.authors shouldBe setOf("Adafruit")
            parsed.dependencies should containExactlyInAnyOrder(
                LibraryDependency(owner = null, name = "Adafruit GFX Library")
            )
        }

        "fall back to the short description if no long description is present" {
            val properties = """
                name=Foo
                sentence=A short description.
            """.trimIndent()

            parseLibraryProperties(properties).manifest.description shouldBe "A short description."
        }

        "parse multiple comma-separated dependencies" {
            val properties = """
                name=Foo
                depends=Bar, Baz
            """.trimIndent()

            parseLibraryProperties(properties).dependencies should containExactlyInAnyOrder(
                LibraryDependency(owner = null, name = "Bar"),
                LibraryDependency(owner = null, name = "Baz")
            )
        }

        "handle a missing depends field" {
            val properties = "name=Foo"

            parseLibraryProperties(properties).dependencies should beEmpty()
        }
    }

    "parseLibraryAuthors()" should {
        "handle a single author object" {
            val json = Json.parseToJsonElement("""{"name": "Jane Doe"}""")

            parseLibraryAuthors(json) shouldBe setOf("Jane Doe")
        }

        "handle a list of author objects" {
            val json = Json.parseToJsonElement(
                """[{"name": "Jane Doe"}, {"name": "John Doe", "email": "john@example.com"}]"""
            )

            parseLibraryAuthors(json) shouldBe setOf("Jane Doe", "John Doe")
        }

        "handle a missing authors field" {
            parseLibraryAuthors(null) shouldBe emptySet()
        }
    }
})
