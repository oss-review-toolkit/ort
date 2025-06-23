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

import org.ossreviewtoolkit.utils.common.nextOrNull
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseIdExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseReferenceExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseWithExceptionExpression
import org.ossreviewtoolkit.utils.spdx.SpdxOperator
import org.ossreviewtoolkit.utils.spdx.toExpression

/**
 * A parser for SPDX expressions. It consumes a sequence of [Token]s and produces an [SpdxExpression].
 *
 * This parser implements the grammar defined in the
 * [SPDX specification](https://spdx.github.io/spdx-spec/v2.2.2/SPDX-license-expressions/):
 *
 * ```
 * license-expression   -> simple-expression | compound-expression
 * compound-expression  -> simple-expression |
 *                         simple-expression "WITH" license-exception-id |
 *                         compound-expression "AND" compound-expression |
 *                         compound-expression "OR" compound-expression |
 *                         "(" compound-expression ")"
 * simple-expression    -> license-id | license-id"+" | license-ref
 * license-ref          -> ["DocumentRef-" idstring ":"] "LicenseRef-" idstring
 * license-exception-id -> <short form license exception identifier in Annex A.2>
 * license-id           -> <short form license identifier in Annex A.1>
 * idstring             -> 1*(ALPHA / DIGIT / "-" / "." )
 * ```
 *
 * To simplify the implementation the grammar is transformed into the following form which implements the operator
 * precedence as part of the grammar. Each line in this grammar corresponds to a method in this class:
 *
 * ```
 * license-expression -> or-expression
 * or-expression      -> and-expression ( "OR" and-expression ) *
 * and-expression     -> primary ( "AND" primary ) *
 * primary            -> "(" license-expression ")" | simple-expression
 * simple-expression  -> ( IDENTIFIER [ "+" ] | [ "DOCUMENTREF" ":" ] LICENSEREF ) [ "WITH" IDENTIFIER ]
 * ```
 *
 * This allows implementing a
 * [recursive descent parser](https://en.wikipedia.org/wiki/Recursive_descent_parser) with
 * [Pratt parsing](https://en.wikipedia.org/wiki/Operator-precedence_parser#Pratt_parsing). The implementation is
 * loosely based on this
 * [example](https://journal.stuffwithstuff.com/2011/03/19/pratt-parsers-expression-parsing-made-easy/) but with many
 * simplifications as the SPDX grammar has only one operator per level of precedence and the parser does not need to be
 * extensible.
 *
 * Also, the rules for `license-id` and `license-exception-id` are changed to allow any valid `idstring` as the
 * [strictness] decides if only the SPDX identifiers are allowed for license and exception ids and therefore these rules
 * cannot be part of the grammar.
 *
 * For backward compatibility with the previously used SPDX expression parser, operators are case-insensitive. This is
 * also planned for future SPDX versions, see https://github.com/spdx/spdx-spec/pull/876.
 */
class SpdxExpressionParser(
    tokens: Sequence<Token>,
    private val strictness: SpdxExpression.Strictness = SpdxExpression.Strictness.ALLOW_ANY
) {
    constructor(
        input: String,
        strictness: SpdxExpression.Strictness = SpdxExpression.Strictness.ALLOW_ANY
    ) : this(SpdxExpressionLexer(input).tokens(), strictness)

    private val iterator = tokens.iterator()
    private var next = iterator.nextOrNull()

    fun parse(): SpdxExpression {
        val result = parseOrExpression()
        if (next != null) throw SpdxExpressionParserException(next)
        return result
    }

    /**
     * Parse an OR expression of the form `or-expression -> and-expression ( "OR" and-expression ) *`.
     */
    private fun parseOrExpression(): SpdxExpression {
        val children = mutableListOf(parseAndExpression())

        while (next is Token.OR) {
            next = iterator.nextOrNull()
            children.add(parseAndExpression())
        }

        return checkNotNull(children.toExpression(SpdxOperator.OR))
    }

    /**
     * Parse an AND expression of the form `and-expression -> primary ( "AND" primary ) *`.
     */
    private fun parseAndExpression(): SpdxExpression {
        val children = mutableListOf(parsePrimary())

        while (next is Token.AND) {
            next = iterator.nextOrNull()
            children.add(parsePrimary())
        }

        return checkNotNull(children.toExpression(SpdxOperator.AND))
    }

    /**
     * Parse a primary of the form `primary -> "(" license-expression ")" | simple-expression`.
     */
    private fun parsePrimary(): SpdxExpression {
        if (next is Token.OPEN) {
            next = iterator.nextOrNull()
            val expression = parseOrExpression()
            consume<Token.CLOSE>()
            return expression
        }

        return parseSimpleExpression()
    }

    /**
     * Parse a simple expression of the form
     * `simple-expression -> ( IDENTIFIER [ "+" ] | [ "DOCUMENTREF" ":" ] LICENSEREF ) [ "WITH" IDENTIFIER ]`.
     */
    private fun parseSimpleExpression(): SpdxExpression {
        val left = when (next) {
            is Token.IDENTIFIER -> {
                val identifier = consume<Token.IDENTIFIER>()

                val orLaterVersion = next is Token.PLUS || identifier.value.endsWith("-or-later")
                if (next is Token.PLUS) next = iterator.nextOrNull()

                SpdxLicenseIdExpression(identifier.value, orLaterVersion).apply { validate(strictness) }
            }

            is Token.DOCUMENTREF -> {
                val documentRef = consume<Token.DOCUMENTREF>()
                consume<Token.COLON>()
                val licenseRef = consume<Token.LICENSEREF>()

                SpdxLicenseReferenceExpression("${documentRef.value}:${licenseRef.value}")
                    .apply { validate(strictness) }
            }

            is Token.LICENSEREF -> {
                val licenseRef = consume<Token.LICENSEREF>()

                SpdxLicenseReferenceExpression(licenseRef.value).apply { validate(strictness) }
            }

            else -> throw SpdxExpressionParserException(next)
        }

        if (next is Token.WITH) {
            next = iterator.nextOrNull()
            val exception = when (next) {
                is Token.IDENTIFIER -> consume<Token.IDENTIFIER>().value
                is Token.LICENSEREF -> consume<Token.LICENSEREF>().value
                is Token.DOCUMENTREF -> "${consume<Token.DOCUMENTREF>().value}:${consume<Token.LICENSEREF>().value}"
                else -> throw SpdxExpressionParserException(
                    next,
                    Token.IDENTIFIER::class,
                    Token.LICENSEREF::class,
                    Token.DOCUMENTREF::class
                )
            }
            return SpdxLicenseWithExceptionExpression(left, exception).apply { validate(strictness) }
        }

        return left
    }

    /**
     * Consume the [next] token and return it if it is of the expected type [T], otherwise throw an
     * [SpdxExpressionParserException].
     */
    private inline fun <reified T : Token> consume(): T {
        val token = next
        if (token !is T) throw SpdxExpressionParserException(token, T::class)
        next = iterator.nextOrNull()
        return token
    }
}
