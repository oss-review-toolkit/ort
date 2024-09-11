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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class PackageJsonTest : WordSpec({
    "parsePackageJson()" should {
        "deserialize the author from a textual node" {
            val json = """
            {
              "author": "Jane Doe <jane.doe@example.com>"
            }
            """.trimIndent()

            val packageJson = parsePackageJson(json)

            packageJson.author.shouldNotBeNull {
                this.name shouldBe "Jane Doe <jane.doe@example.com>"
            }
        }

        "deserialize the author from an object node" {
            val json = """
            {
              "author": {
                "name": "John Doe"
              }
            }
            """.trimIndent()

            val packageJson = parsePackageJson(json)

            packageJson.author.shouldNotBeNull {
                this.name shouldBe "John Doe"
            }
        }
    }
})
