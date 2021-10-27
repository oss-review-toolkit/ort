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

package org.ossreviewtoolkit.utils.spdx

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.antlr.v4.runtime.CharStreams

fun getTokensByTypeForExpression(expression: String): List<Pair<Int, String>> {
    val charStream = CharStreams.fromString(expression)
    val lexer = SpdxExpressionLexer(charStream)

    lexer.removeErrorListeners()
    lexer.addErrorListener(SpdxErrorListener())

    return lexer.allTokens.map { it.type to it.text }
}

class SpdxExpressionLexerTest : WordSpec({
    "SpdxExpressionLexer" should {
        "create the correct tokens for a valid expression" {
            val expression = "(license-1 AND OR license.2 WITH 0LiCeNsE-3+) and (or with license-4 license-.5(("

            val tokens = getTokensByTypeForExpression(expression)

            tokens should containExactly(
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
})
