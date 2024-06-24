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

package org.ossreviewtoolkit.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should

class OrtEmptyLineAfterBlockTest : WordSpec({
    val rule = OrtEmptyLineAfterBlock(Config.empty)

    "OrtEmptyLineAfterBlock rule" should {
        "succeed if an empty line is inserted after a block" {
            val findings = rule.lint(
                // language=Kotlin
                """
                fun foo() {
                    if (true) {
                        println("Inside block.")
                    }

                    println("This statement is valid.")
                }
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "succeed if no empty line is inserted after a one-liner block" {
            val findings = rule.lint(
                // language=Kotlin
                """
                fun foo() {
                    if (true) { println("Inside block.") }
                    println("This statement is valid.")
                }
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "succeed if a block has no siblings" {
            val findings = rule.lint(
                // language=Kotlin
                """
                fun foo() {
                    if (true) {
                        println("Inside block.")
                    }
                }
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "succeed for a chain of blocks" {
            val findings = rule.lint(
                // language=Kotlin
                """
                fun foo() =
                    if (true) {
                        println("Inside if.")
                    } else {
                        println("Inside else.")
                    }.also { println("Inside also.") }
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "fail if no empty line is inserted between a block and a call" {
            val findings = rule.lint(
                // language=Kotlin
                """
                fun foo() {
                    if (true) {
                        println("Inside block.")
                    }
                    println("This statement is invalid.")
                }
                """.trimIndent()
            )

            findings should haveSize(1)
        }

        "fail if no empty line is inserted between a block and another block" {
            val findings = rule.lint(
                // language=Kotlin
                """
                fun foo() {
                    if (true) {
                        println("Inside first block.")
                    }
                    if (true) {
                        println("Inside second block.")
                    }
                }
                """.trimIndent()
            )

            findings should haveSize(1)
        }

        "fail if no empty line is inserted after a multi-line lambda argument" {
            val findings = rule.lint(
                // language=Kotlin
                """
                fun foo() {
                    require(true) {
                        println("Inside require.")
                    }
                    println("Outside require.")
                }
                """.trimIndent()
            )

            findings should haveSize(1)
        }
    }
})
