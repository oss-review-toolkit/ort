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

package org.ossreviewtoolkit.plugins.packagemanagers.bazel

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class StarlarkParserTest : WordSpec({
    "Lexer" should {
        "produce tokens" {
            val moduleFileContent = """module(name = "yaml-cpp")"""
            val lexer = Lexer(moduleFileContent)

            lexer.nextToken() shouldBe Token(TokenType.IDENTIFIER, "module")
            lexer.nextToken() shouldBe Token(TokenType.LPAREN, "(")
            lexer.nextToken() shouldBe Token(TokenType.IDENTIFIER, "name")
            lexer.nextToken() shouldBe Token(TokenType.EQUALS, "=")
            lexer.nextToken() shouldBe Token(TokenType.STRING, "yaml-cpp")
            lexer.nextToken() shouldBe Token(TokenType.RPAREN, ")")
            lexer.nextToken() shouldBe Token(TokenType.EOF, "")
        }

        "skip comments" {
            val moduleFileContent = """
                # yaml-cpp is a YAML parser and emitter in c++ matching the YAML specification.

                module(name="yaml-cpp") # TODO add version
            """.trimIndent()
            val lexer = Lexer(moduleFileContent)

            lexer.nextToken() shouldBe Token(TokenType.IDENTIFIER, "module")
        }

        "support multi-line strings" {
            val moduleFileContent = """
                ""${'"'}
                yaml-cpp is a YAML parser and emitter in c++
                matching the YAML specification.
                ""${'"'}

                module(name=""${'"'}yaml-cpp""${'"'})
            """.trimIndent()
            val lexer = Lexer(moduleFileContent)

            lexer.nextToken() shouldBe Token(
                TokenType.STRING,
                "\nyaml-cpp is a YAML parser and emitter in c++\nmatching the YAML specification.\n"
            )

            lexer.nextToken() shouldBe Token(TokenType.IDENTIFIER, "module")
            lexer.nextToken() shouldBe Token(TokenType.LPAREN, "(")
            lexer.nextToken() shouldBe Token(TokenType.IDENTIFIER, "name")
            lexer.nextToken() shouldBe Token(TokenType.EQUALS, "=")
            lexer.nextToken() shouldBe Token(TokenType.STRING, "yaml-cpp")
        }
    }

    "Parser" should {
        "handle a single line module directive" {
            val moduleFileContent = """module(name = "yaml-cpp", compatibility_level = 1, version = "0.8.0")"""

            val result = Parser(moduleFileContent).parse()
            result.module shouldNotBeNull {
                name shouldBe "yaml-cpp"
                version shouldBe "0.8.0"
                compatibilityLevel shouldBe 1
            }

            result.dependencies should beEmpty()
        }

        "handle a multi line module directive and dependency declarations" {
            val moduleFileContent = """
                ""${'"'}
                yaml-cpp is a YAML parser and emitter in c++ matching the YAML specification.
                ""${'"'}

                module(
                    name= "yaml-cpp",
                    compatibility_level=1,
                    version ="0.8.0",
                )

                bazel_dep(name = "platforms",version = "0.0.7")
                bazel_dep(name="rules_cc", version="0.0.8")
                bazel_dep(name="googletest",version="1.14.0" ,dev_dependency= True)
            """.trimIndent()

            val result = Parser(moduleFileContent).parse()
            result.module shouldNotBeNull {
                name shouldBe "yaml-cpp"
                version shouldBe "0.8.0"
                compatibilityLevel shouldBe 1
            }

            result.dependencies.run {
                shouldHaveSize(3)

                shouldContainExactly(
                    BazelDepDirective(name = "platforms", version = "0.0.7", devDependency = false),
                    BazelDepDirective(name = "rules_cc", version = "0.0.8", devDependency = false),
                    BazelDepDirective(name = "googletest", version = "1.14.0", devDependency = true)
                )
            }
        }

        "ignore everything other than module and bazel_dep directives" {
            val moduleFileContent = """
                module(
                    name = "toolchain_utils",
                    version = "1.0.0-beta.1",
                    bazel_compatibility = [
                        ">=7.0.0",
                    ],
                    compatibility_level = 1,
                )
                
                bazel_dep(name = "bazel_skylib", version = "1.4.2")
                bazel_dep(name = "platforms", version = "0.0.7")
                
                triplet = use_repo_rule("//toolchain/local/triplet:defs.bzl", "toolchain_local_triplet")
                
                triplet(
                    name = "local",
                )
                
                launcher = use_repo_rule("//toolchain/launcher:repository.bzl", "launcher")
                
                launcher(
                    name = "launcher",
                )
            """.trimIndent()

            val result = Parser(moduleFileContent).parse()
            result.module shouldNotBeNull {
                name shouldBe "toolchain_utils"
                version shouldBe "1.0.0-beta.1"
                compatibilityLevel shouldBe 1
            }

            result.dependencies.run {
                shouldHaveSize(2)

                shouldContainExactly(
                    BazelDepDirective(name = "bazel_skylib", version = "1.4.2", devDependency = false),
                    BazelDepDirective(name = "platforms", version = "0.0.7", devDependency = false)
                )
            }
        }
    }
})
