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
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.spdx.SpdxCompoundExpression
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseIdExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseReferenceExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseWithExceptionExpression
import org.ossreviewtoolkit.utils.spdx.SpdxOperator

class SpdxExpressionParserTest : FunSpec({
    context("identifiers") {
        verifyExpressions(
            "a" to SpdxLicenseIdExpression("a"),
            "a+" to SpdxLicenseIdExpression("a", orLaterVersion = true),
            "a-or-later" to SpdxLicenseIdExpression("a-or-later", orLaterVersion = true),
            "Apache-2.0" to SpdxLicenseIdExpression("Apache-2.0"),
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-" to SpdxLicenseIdExpression(
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-"
            )
        )
    }

    context("license references") {
        verifyExpressions(
            "LicenseRef-a" to SpdxLicenseReferenceExpression("LicenseRef-a"),
            "LicenseRef-ort-license" to SpdxLicenseReferenceExpression("LicenseRef-ort-license"),
            "DocumentRef-a:LicenseRef-b" to SpdxLicenseReferenceExpression("DocumentRef-a:LicenseRef-b")
        )
    }

    context("WITH expressions") {
        verifyExpressions(
            "a WITH b" to SpdxLicenseWithExceptionExpression(
                SpdxLicenseIdExpression("a"),
                "b"
            ),
            "GPL-3.0-or-later WITH GPL-3.0-linking-exception" to SpdxLicenseWithExceptionExpression(
                SpdxLicenseIdExpression("GPL-3.0-or-later", orLaterVersion = true),
                "GPL-3.0-linking-exception"
            )
        )
    }

    context("AND expressions") {
        verifyExpressions(
            "a AND b" to SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxLicenseIdExpression("a"),
                SpdxLicenseIdExpression("b")
            ),
            "a AND b AND c" to SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxLicenseIdExpression("a"),
                SpdxLicenseIdExpression("b"),
                SpdxLicenseIdExpression("c")
            ),
            "a AND b AND c AND d" to SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxLicenseIdExpression("a"),
                SpdxLicenseIdExpression("b"),
                SpdxLicenseIdExpression("c"),
                SpdxLicenseIdExpression("d")
            )
        )
    }

    context("OR expressions") {
        verifyExpressions(
            "a OR b" to SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxLicenseIdExpression("a"),
                SpdxLicenseIdExpression("b")
            ),
            "a OR b OR c" to SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxLicenseIdExpression("a"),
                SpdxLicenseIdExpression("b"),
                SpdxLicenseIdExpression("c")
            ),
            "a OR b OR c OR d" to SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxLicenseIdExpression("a"),
                SpdxLicenseIdExpression("b"),
                SpdxLicenseIdExpression("c"),
                SpdxLicenseIdExpression("d")
            )
        )
    }

    context("compound expressions") {
        verifyExpressions(
            "(a)" to SpdxLicenseIdExpression("a"),
            "(a AND b)" to SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxLicenseIdExpression("a"),
                SpdxLicenseIdExpression("b")
            ),
            "a AND (b OR c)" to SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxLicenseIdExpression("a"),
                SpdxCompoundExpression(
                    SpdxOperator.OR,
                    SpdxLicenseIdExpression("b"),
                    SpdxLicenseIdExpression("c")
                )
            ),
            "(a AND b) OR c" to SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxCompoundExpression(
                    SpdxOperator.AND,
                    SpdxLicenseIdExpression("a"),
                    SpdxLicenseIdExpression("b")
                ),
                SpdxLicenseIdExpression("c")
            ),
            "(a OR b) AND (c OR d)" to SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxCompoundExpression(
                    SpdxOperator.OR,
                    SpdxLicenseIdExpression("a"),
                    SpdxLicenseIdExpression("b")
                ),
                SpdxCompoundExpression(
                    SpdxOperator.OR,
                    SpdxLicenseIdExpression("c"),
                    SpdxLicenseIdExpression("d")
                )
            )
        )
    }

    context("operator precedence") {
        verifyExpressions(
            "a AND b OR c" to SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxCompoundExpression(
                    SpdxOperator.AND,
                    SpdxLicenseIdExpression("a"),
                    SpdxLicenseIdExpression("b")
                ),
                SpdxLicenseIdExpression("c")
            ),
            "a OR b AND c" to SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxLicenseIdExpression("a"),
                SpdxCompoundExpression(
                    SpdxOperator.AND,
                    SpdxLicenseIdExpression("b"),
                    SpdxLicenseIdExpression("c")
                )
            ),
            "a OR b AND c OR d" to SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxLicenseIdExpression("a"),
                SpdxCompoundExpression(
                    SpdxOperator.AND,
                    SpdxLicenseIdExpression("b"),
                    SpdxLicenseIdExpression("c")
                ),
                SpdxLicenseIdExpression("d")
            ),
            "a AND b OR c AND d" to SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxCompoundExpression(
                    SpdxOperator.AND,
                    SpdxLicenseIdExpression("a"),
                    SpdxLicenseIdExpression("b")
                ),
                SpdxCompoundExpression(
                    SpdxOperator.AND,
                    SpdxLicenseIdExpression("c"),
                    SpdxLicenseIdExpression("d")
                )
            ),
            "a WITH b AND c OR d" to SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxCompoundExpression(
                    SpdxOperator.AND,
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseIdExpression("a"),
                        "b"
                    ),
                    SpdxLicenseIdExpression("c")
                ),
                SpdxLicenseIdExpression("d")
            ),
            "a OR b AND c WITH d" to SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxLicenseIdExpression("a"),
                SpdxCompoundExpression(
                    SpdxOperator.AND,
                    SpdxLicenseIdExpression("b"),
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseIdExpression("c"),
                        "d"
                    )
                )
            )
        )
    }

    context("invalid expressions") {
        verifyExceptions(
            "a a" to SpdxExpressionParserException(Token.IDENTIFIER(3, 3, "a")),
            "a AND" to SpdxExpressionParserException(null),
            "AND a" to SpdxExpressionParserException(Token.AND(1)),
            "a OR" to SpdxExpressionParserException(null),
            "OR a" to SpdxExpressionParserException(Token.OR(1)),
            "a ( b" to SpdxExpressionParserException(Token.OPEN(3)),
            "a ) b" to SpdxExpressionParserException(Token.CLOSE(3)),
            "a WITH b+" to SpdxExpressionParserException(Token.PLUS(9)),
            "a WITH WITH b" to SpdxExpressionParserException(
                Token.WITH(8),
                Token.IDENTIFIER::class,
                Token.LICENSEREF::class,
                Token.DOCUMENTREF::class
            ),
            "LicenseRef-a+ WITH b" to SpdxExpressionParserException(Token.PLUS(13)),
            "a:b" to SpdxExpressionParserException(Token.COLON(2)),
            "((a AND b) OR c" to SpdxExpressionParserException(null, Token.CLOSE::class)
        )
    }
})

/**
 * Verify that the [SpdxExpressionParser] produces the expected [SpdxExpression] for the given input.
 */
private suspend fun FunSpecContainerScope.verifyExpressions(vararg input: Pair<String, SpdxExpression>) {
    withData(
        nameFn = { it.first },
        input.asList()
    ) { (expression, expectedExpression) ->
        SpdxExpressionParser(expression, SpdxExpression.Strictness.ALLOW_ANY).parse() shouldBe expectedExpression
    }
}

/**
 * Verify that the [SpdxExpressionParser] produces the expected [SpdxExpressionParserException] for the given input.
 */
private suspend fun FunSpecContainerScope.verifyExceptions(vararg input: Pair<String, SpdxExpressionParserException>) {
    withData(
        nameFn = { it.first },
        input.asList()
    ) { (expression, expectedException) ->
        shouldThrow<SpdxExpressionParserException> {
            SpdxExpressionParser(expression, SpdxExpression.Strictness.ALLOW_ANY).parse()
        }.apply {
            token shouldBe expectedException.token
            expectedTokenTypes shouldBe expectedException.expectedTokenTypes
        }
    }
}
