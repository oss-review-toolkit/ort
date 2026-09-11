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
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class PlatformResolverTest : WordSpec({
    "parsePlatformTree()" should {
        "parse a platform and its direct package dependencies, including a platform_packages override" {
            val output = """
                Resolving uno dependencies...
                Platform atmelavr @ 5.3.0 (required: atmelavr, /home/user/.platformio/platforms/atmelavr)
                ├── framework-arduino-avr @ 5.4.0 (required: platformio/framework-arduino-avr @ ~5.4.0)
                ├── tool-scons @ 4.40801.0 (required: platformio/tool-scons @ ~4.40801.0)
                └── toolchain-atmelavr @ 1.70300.191015 (required: platformio/toolchain-atmelavr @ ~1.70300.0)

            """.trimIndent()

            val (root, children) = checkNotNull(parsePlatformTree(output))

            root shouldBe ResolvedPackageRef("atmelavr", "5.3.0")
            children should containExactly(
                ResolvedPackageRef("framework-arduino-avr", "5.4.0"),
                ResolvedPackageRef("tool-scons", "4.40801.0"),
                ResolvedPackageRef("toolchain-atmelavr", "1.70300.191015")
            )
        }

        "parse a platform without any package dependencies" {
            val output = """
                Resolving native dependencies...
                Platform native @ 1.2.1 (required: native)

            """.trimIndent()

            val (root, children) = checkNotNull(parsePlatformTree(output))

            root shouldBe ResolvedPackageRef("native", "1.2.1")
            children should beEmpty()
        }

        "return null if the output does not contain a platform" {
            val output = "Resolving uno dependencies...\n"

            parsePlatformTree(output) should beNull()
        }
    }

    "parsePlatformPackageNamesByType()" should {
        "identify the mutually exclusive framework variants of a multi-chip-family platform like ststm32" {
            val json = """
                {
                  "packages": {
                    "toolchain-gccarmnoneeabi": { "type": "toolchain", "owner": "platformio" },
                    "framework-stm32cubef1": { "type": "framework", "optional": true, "owner": "platformio" },
                    "framework-stm32cubeh7": { "type": "framework", "optional": true, "owner": "platformio" },
                    "tool-ldscripts-ststm32": { "optional": true, "owner": "platformio" }
                  }
                }
            """.trimIndent()

            val frameworkNames = setOf("framework-stm32cubef1", "framework-stm32cubeh7")

            parsePlatformPackageNamesByType(json, "framework") shouldBe frameworkNames
            parsePlatformPackageNamesByType(json, "toolchain") shouldBe setOf("toolchain-gccarmnoneeabi")
        }

        "return an empty set if there is no packages section" {
            parsePlatformPackageNamesByType("{}", "framework") should beEmpty()
        }
    }
})
