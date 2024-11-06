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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

class YarnTest : WordSpec({
    "parseYarnInfo" should {
        val fileWithRetries = File("src/test/assets/yarn-package-data-with-retry.txt")

        "parse a valid JSON string" {
            val json = fileWithRetries.readLines()[3]

            val packageJson = parseYarnInfo(json, "")

            packageJson?.homepage shouldBe "https://github.com/watson/bonjour/local"
        }

        "return null for invalid input" {
            val json = """
                This is not valid JSON.
                Also not on any line.
            """.trimIndent()

            parseYarnInfo(json, "") should beNull()
        }

        "parse a JSON string with multiple objects" {
            val json = fileWithRetries.readText()

            val packageJson = parseYarnInfo(json, "")

            packageJson?.homepage shouldBe "https://github.com/watson/bonjour/local"
        }

        "handle a type property that is not a primitive" {
            val json = File("src/test/assets/yarn-package-data-with-wrong-type.txt").readText()

            val packageJson = parseYarnInfo(json, "")

            packageJson?.homepage shouldBe "https://github.com/watson/bonjour/local"
        }
    }
})
