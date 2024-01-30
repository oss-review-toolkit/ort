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

package org.ossreviewtoolkit.utils.spdx.parser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.scopes.FunSpecContainerScope
import io.kotest.datatest.withData
import io.kotest.matchers.sequences.shouldContainExactly
import io.kotest.matchers.shouldBe

class SpdxExpressionLexerTest : FunSpec({
    context("identifiers") {
        verifyTokens(
            "a" to sequenceOf(
                Token.IDENTIFIER(1, 1, "a")
            ),
            "a+" to sequenceOf(
                Token.IDENTIFIER(1, 1, "a"),
                Token.PLUS(2)
            ),
            "a-or-later" to sequenceOf(
                Token.IDENTIFIER(1, 10, "a-or-later")
            ),
            "Apache-2.0" to sequenceOf(
                Token.IDENTIFIER(1, 10, "Apache-2.0")
            ),
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-" to sequenceOf(
                Token.IDENTIFIER(1, 64, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-")
            )
        )
    }

    context("license references") {
        verifyTokens(
            "LicenseRef-a" to sequenceOf(
                Token.LICENSEREF(1, 12, "LicenseRef-a")
            ),
            "LicenseRef-ort-license" to sequenceOf(
                Token.LICENSEREF(1, 22, "LicenseRef-ort-license")
            ),
            "DocumentRef-a:LicenseRef-b" to sequenceOf(
                Token.DOCUMENTREF(1, 13, "DocumentRef-a"),
                Token.COLON(14),
                Token.LICENSEREF(15, 26, "LicenseRef-b")
            )
        )
    }

    context("WITH expressions") {
        verifyTokens(
            "a WITH b" to sequenceOf(
                Token.IDENTIFIER(1, 1, "a"),
                Token.WITH(3),
                Token.IDENTIFIER(8, 8, "b")
            ),
            "GPL-3.0-or-later WITH GPL-3.0-linking-exception" to sequenceOf(
                Token.IDENTIFIER(1, 16, "GPL-3.0-or-later"),
                Token.WITH(18),
                Token.IDENTIFIER(23, 47, "GPL-3.0-linking-exception")
            )
        )
    }

    context("AND expressions") {
        verifyTokens(
            "AND" to sequenceOf(
                Token.AND(1)
            ),
            "AND AND" to sequenceOf(
                Token.AND(1),
                Token.AND(5)
            ),
            "a AND b" to sequenceOf(
                Token.IDENTIFIER(1, 1, "a"),
                Token.AND(3),
                Token.IDENTIFIER(7, 7, "b")
            ),
            "a AND b AND c" to sequenceOf(
                Token.IDENTIFIER(1, 1, "a"),
                Token.AND(3),
                Token.IDENTIFIER(7, 7, "b"),
                Token.AND(9),
                Token.IDENTIFIER(13, 13, "c")
            ),
            "a AND b AND c AND d" to sequenceOf(
                Token.IDENTIFIER(1, 1, "a"),
                Token.AND(3),
                Token.IDENTIFIER(7, 7, "b"),
                Token.AND(9),
                Token.IDENTIFIER(13, 13, "c"),
                Token.AND(15),
                Token.IDENTIFIER(19, 19, "d")
            )
        )
    }

    context("OR expressions") {
        verifyTokens(
            "OR" to sequenceOf(
                Token.OR(1)
            ),
            "OR OR" to sequenceOf(
                Token.OR(1),
                Token.OR(4)
            ),
            "a OR b" to sequenceOf(
                Token.IDENTIFIER(1, 1, "a"),
                Token.OR(3),
                Token.IDENTIFIER(6, 6, "b")
            ),
            "a OR b OR c" to sequenceOf(
                Token.IDENTIFIER(1, 1, "a"),
                Token.OR(3),
                Token.IDENTIFIER(6, 6, "b"),
                Token.OR(8),
                Token.IDENTIFIER(11, 11, "c")
            ),
            "a OR b OR c OR d" to sequenceOf(
                Token.IDENTIFIER(1, 1, "a"),
                Token.OR(3),
                Token.IDENTIFIER(6, 6, "b"),
                Token.OR(8),
                Token.IDENTIFIER(11, 11, "c"),
                Token.OR(13),
                Token.IDENTIFIER(16, 16, "d")
            )
        )
    }

    context("lowercase operators") {
        verifyTokens(
            "and" to sequenceOf(Token.AND(1)),
            "or" to sequenceOf(Token.OR(1)),
            "with" to sequenceOf(Token.WITH(1))
        )
    }

    context("mixed-case operators") {
        verifyTokens(
            "And" to sequenceOf(Token.AND(1)),
            "aND" to sequenceOf(Token.AND(1)),
            "Or" to sequenceOf(Token.OR(1)),
            "oR" to sequenceOf(Token.OR(1)),
            "With" to sequenceOf(Token.WITH(1)),
            "wITH" to sequenceOf(Token.WITH(1))
        )
    }

    context("compound expressions") {
        verifyTokens(
            "()" to sequenceOf(
                Token.OPEN(1),
                Token.CLOSE(2)
            ),
            "(a)" to sequenceOf(
                Token.OPEN(1),
                Token.IDENTIFIER(2, 2, "a"),
                Token.CLOSE(3)
            ),
            "(a AND b)" to sequenceOf(
                Token.OPEN(1),
                Token.IDENTIFIER(2, 2, "a"),
                Token.AND(4),
                Token.IDENTIFIER(8, 8, "b"),
                Token.CLOSE(9)
            ),
            "a AND (b OR c)" to sequenceOf(
                Token.IDENTIFIER(1, 1, "a"),
                Token.AND(3),
                Token.OPEN(7),
                Token.IDENTIFIER(8, 8, "b"),
                Token.OR(10),
                Token.IDENTIFIER(13, 13, "c"),
                Token.CLOSE(14)
            ),
            "(a AND b) OR c" to sequenceOf(
                Token.OPEN(1),
                Token.IDENTIFIER(2, 2, "a"),
                Token.AND(4),
                Token.IDENTIFIER(8, 8, "b"),
                Token.CLOSE(9),
                Token.OR(11),
                Token.IDENTIFIER(14, 14, "c")
            ),
            "(a OR b) AND (c OR d)" to sequenceOf(
                Token.OPEN(1),
                Token.IDENTIFIER(2, 2, "a"),
                Token.OR(4),
                Token.IDENTIFIER(7, 7, "b"),
                Token.CLOSE(8),
                Token.AND(10),
                Token.OPEN(14),
                Token.IDENTIFIER(15, 15, "c"),
                Token.OR(17),
                Token.IDENTIFIER(20, 20, "d"),
                Token.CLOSE(21)
            )
        )
    }

    context("invalid expressions") {
        verifyExceptions(
            "_" to SpdxExpressionLexerException('_', 1),
            "a_" to SpdxExpressionLexerException('_', 2),
            "a AND {b OR c}" to SpdxExpressionLexerException('{', 7),
            "a\nb" to SpdxExpressionLexerException('\n', 2),
            "a\tb" to SpdxExpressionLexerException('\t', 2),
            "LicenseRef-ort-lißense" to SpdxExpressionLexerException('ß', 18)
        )
    }
})

/**
 * Verify that the [SpdxExpressionLexer] produces the expected tokens for the given input.
 */
private suspend fun FunSpecContainerScope.verifyTokens(vararg input: Pair<String, Sequence<Token>>) {
    withData(
        nameFn = { it.first },
        input.asSequence()
    ) { (expression, expectedTokens) ->
        SpdxExpressionLexer(expression).tokens() shouldContainExactly expectedTokens
    }
}

/**
 * Verify that the [SpdxExpressionLexer] produces the expected [SpdxExpressionLexerException] for the given input.
 */
private suspend fun FunSpecContainerScope.verifyExceptions(vararg input: Pair<String, SpdxExpressionLexerException>) {
    withData(
        nameFn = { it.first },
        input.asSequence()
    ) { (expression, expectedException) ->
        shouldThrow<SpdxExpressionLexerException> {
            SpdxExpressionLexer(expression).tokens().toList()
        }.apply {
            char shouldBe expectedException.char
            position shouldBe expectedException.position
        }
    }
}
