/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.spdx

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

class SpdxExpressionLexerTest : WordSpec() {
    init {
        "SpdxExpressionLexer" should {
            "create the correct tokens for a valid expression" {
                val expression = "(license-1 AND OR license.2 WITH 0LiCeNsE-3+) and (or with license-4 license-.5(("

                val tokens = getTokensByTypeForExpression(expression)

                tokens shouldBe listOf(
                        SpdxExpressionLexer.OPEN to "(",
                        SpdxExpressionLexer.IDSTRING to "license-1",
                        SpdxExpressionLexer.AND to "AND",
                        SpdxExpressionLexer.OR to "OR",
                        SpdxExpressionLexer.IDSTRING to "license.2",
                        SpdxExpressionLexer.WITH to "WITH",
                        SpdxExpressionLexer.IDSTRING to "0LiCeNsE-3",
                        SpdxExpressionLexer.PLUS to "+",
                        SpdxExpressionLexer.CLOSE to ")",
                        SpdxExpressionLexer.AND to "and",
                        SpdxExpressionLexer.OPEN to "(",
                        SpdxExpressionLexer.OR to "or",
                        SpdxExpressionLexer.WITH to "with",
                        SpdxExpressionLexer.IDSTRING to "license-4",
                        SpdxExpressionLexer.IDSTRING to "license-.5",
                        SpdxExpressionLexer.OPEN to "(",
                        SpdxExpressionLexer.OPEN to "("
                )
            }

            "fail for an invalid expression" {
                val expression = "/"

                val exception = shouldThrow<SpdxException> {
                    getTokensByTypeForExpression(expression)
                }

                exception.message shouldBe "token recognition error at: '/'"
            }
        }
    }

    private fun getTokensByTypeForExpression(expression: String): List<Pair<Int, String>> {
        val charStream = CharStreams.fromString(expression)
        val lexer = SpdxExpressionLexer(charStream)
        lexer.removeErrorListeners()
        lexer.addErrorListener(object : BaseErrorListener() {
            override fun syntaxError(
                    recognizer: Recognizer<*, *>?,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String?,
                    e: RecognitionException?
            ) {
                throw SpdxException(msg)
            }
        })

        return lexer.allTokens.map { it.type to it.text }
    }
}
