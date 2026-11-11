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

package org.ossreviewtoolkit.detekt

import dev.detekt.api.Config
import dev.detekt.api.modifiedText
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import dev.detekt.test.utils.compileContentForTest

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class OrtSingleLineKDocTest : WordSpec({
    "OrtSingleLineKDoc rule" should {
        "report a multi-line KDoc with a single line of content" {
            val findings = OrtSingleLineKDoc(Config.empty).lint(
                // language=Kotlin
                """
                /**
                 * A class.
                 */
                class A
                """.trimIndent()
            )

            findings should haveSize(1)
        }

        "report a multi-line KDoc with blank lines around the content" {
            val findings = OrtSingleLineKDoc(Config.empty).lint(
                // language=Kotlin
                """
                /**
                 *
                 * A class.
                 *
                 */
                class A
                """.trimIndent()
            )

            findings should haveSize(1)
        }

        "not report a single-line KDoc" {
            val findings = OrtSingleLineKDoc(Config.empty).lint(
                // language=Kotlin
                """
                /** A class. */
                class A
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "not report a KDoc with multiple lines of content" {
            val findings = OrtSingleLineKDoc(Config.empty).lint(
                // language=Kotlin
                """
                /**
                 * A class.
                 * With a second line.
                 */
                class A
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "not report a KDoc with a block tag" {
            val findings = OrtSingleLineKDoc(Config.empty).lint(
                // language=Kotlin
                """
                /**
                 * @param value The value.
                 */
                class A(val value: Int)
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "not report a KDoc that would exceed the default maximum line length on a single line" {
            val findings = OrtSingleLineKDoc(Config.empty).lint(
                // language=Kotlin
                """
                class A {
                    /**
                     * ${"x".repeat(113)}
                     */
                    fun f() = Unit
                }
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "use the maximum line length configured for the MaxLineLength rule" {
            val rootConfig = TestConfig(
                "style" to mapOf("MaxLineLength" to mapOf("maxLineLength" to 20)),
                "ORT" to mapOf("OrtSingleLineKDoc" to emptyMap<String, Any>())
            )
            val rule = OrtSingleLineKDoc(rootConfig.subConfig("ORT").subConfig("OrtSingleLineKDoc"))

            val findings = rule.lint(
                // language=Kotlin
                """
                /**
                 * Fits in 20.
                 */
                class A

                /**
                 * Does not fit in 20.
                 */
                class B
                """.trimIndent()
            )

            findings should haveSize(1)
            findings.single().entity.location.source.line shouldBe 1
        }

        "convert KDocs to a single line if auto-correct is enabled" {
            val file = compileContentForTest(
                // language=Kotlin
                """
                /**
                 * A class.
                 */
                class A {
                    /**
                     * A function.
                     */
                    fun f() = Unit

                    /**
                     * A function.
                     * With a second line.
                     */
                    fun g() = Unit
                }
                """.trimIndent()
            )

            OrtSingleLineKDoc(TestConfig("autoCorrect" to true)).lint(file)

            file.modifiedText shouldBe """
                /** A class. */
                class A {
                    /** A function. */
                    fun f() = Unit

                    /**
                     * A function.
                     * With a second line.
                     */
                    fun g() = Unit
                }
            """.trimIndent()
        }

        "not convert KDocs if the rule is suppressed" {
            val file = compileContentForTest(
                // language=Kotlin
                """
                @Suppress("OrtSingleLineKDoc")
                class A {
                    /**
                     * Suppressed via the enclosing class.
                     */
                    fun f() = Unit
                }

                class B {
                    /**
                     * Suppressed via the rule set and rule.
                     */
                    @Suppress("detekt:ORT:OrtSingleLineKDoc")
                    fun f() = Unit

                    /**
                     * Not suppressed.
                     */
                    fun g() = Unit
                }
                """.trimIndent()
            )

            OrtSingleLineKDoc(TestConfig("autoCorrect" to true)).lint(file)

            file.modifiedText shouldBe """
                @Suppress("OrtSingleLineKDoc")
                class A {
                    /**
                     * Suppressed via the enclosing class.
                     */
                    fun f() = Unit
                }

                class B {
                    /**
                     * Suppressed via the rule set and rule.
                     */
                    @Suppress("detekt:ORT:OrtSingleLineKDoc")
                    fun f() = Unit

                    /** Not suppressed. */
                    fun g() = Unit
                }
            """.trimIndent()
        }

        "not convert KDocs if the rule is suppressed via an alias" {
            val file = compileContentForTest(
                // language=Kotlin
                """
                @Suppress("SingleLineKDoc")
                class A {
                    /**
                     * Suppressed via an alias.
                     */
                    fun f() = Unit
                }
                """.trimIndent()
            )

            OrtSingleLineKDoc(TestConfig("autoCorrect" to true, "aliases" to listOf("SingleLineKDoc"))).lint(file)

            file.modifiedText should beNull()
        }

        "not convert KDocs on elements matching ignoreAnnotated" {
            val file = compileContentForTest(
                // language=Kotlin
                """
                package a

                annotation class Generated

                @Generated
                class A {
                    /**
                     * Ignored via an annotation.
                     */
                    fun f() = Unit
                }
                """.trimIndent()
            )

            val config = TestConfig("autoCorrect" to true, "ignoreAnnotated" to listOf("Generated"))
            OrtSingleLineKDoc(config).lint(file)

            file.modifiedText should beNull()
        }

        "not convert KDocs on functions matching ignoreFunction" {
            val file = compileContentForTest(
                // language=Kotlin
                """
                package a

                /**
                 * Ignored via the function name.
                 */
                fun f() = Unit
                """.trimIndent()
            )

            OrtSingleLineKDoc(TestConfig("autoCorrect" to true, "ignoreFunction" to listOf("a.f"))).lint(file)

            file.modifiedText should beNull()
        }

        "not convert KDocs if the rule is suppressed for the file" {
            val file = compileContentForTest(
                // language=Kotlin
                """
                @file:Suppress("ORT")

                package a

                /**
                 * A class.
                 */
                class A
                """.trimIndent()
            )

            OrtSingleLineKDoc(TestConfig("autoCorrect" to true)).lint(file)

            file.modifiedText should beNull()
        }

        "keep Markdown emphasis on the first line" {
            val file = compileContentForTest(
                // language=Kotlin
                """
                /** *Important*
                 */
                class A
                """.trimIndent()
            )

            OrtSingleLineKDoc(TestConfig("autoCorrect" to true)).lint(file)

            file.modifiedText shouldBe """
                /** *Important* */
                class A
            """.trimIndent()
        }

        "not report a KDoc with an indented code block" {
            val findings = OrtSingleLineKDoc(Config.empty).lint(
                // language=Kotlin
                """
                /**
                 *     val answer = 42
                 */
                class A
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "not report a KDoc with a tab-indented code block" {
            val findings = OrtSingleLineKDoc(Config.empty).lint("/**\n *\tval answer = 42\n */\nclass A\n")

            findings should beEmpty()
        }

        "treat indentation on a line without an asterisk as the KDoc parser does" {
            // Without a leading asterisk, the KDoc parser does not treat indented text as a code block, so the content
            // renders as plain text and can be converted.
            val file = compileContentForTest(
                // language=Kotlin
                """
                /**
                     val answer = 42
                 */
                class A
                """.trimIndent()
            )

            OrtSingleLineKDoc(TestConfig("autoCorrect" to true)).lint(file)

            file.modifiedText shouldBe """
                /** val answer = 42 */
                class A
            """.trimIndent()
        }

        "not report a KDoc with a block tag on a later line" {
            val findings = OrtSingleLineKDoc(Config.empty).lint(
                // language=Kotlin
                """
                /**
                 *
                 * @return Nothing.
                 */
                fun f() = Unit
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "report a KDoc with a line without an asterisk that is not indented as code" {
            val findings = OrtSingleLineKDoc(Config.empty).lint(
                // language=Kotlin
                """
                /**
                  A class.
                 */
                class A
                """.trimIndent()
            )

            findings should haveSize(1)
        }

        "not modify the file if auto-correct is disabled" {
            val file = compileContentForTest(
                // language=Kotlin
                """
                /**
                 * A class.
                 */
                class A
                """.trimIndent()
            )

            OrtSingleLineKDoc(Config.empty).lint(file)

            file.modifiedText should beNull()
        }
    }
})
