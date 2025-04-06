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

/**
 * A lexer for SPDX expressions. It consumes a sequence of characters and produces a sequence of [Token]s. For details
 * on the grammar see [SpdxExpressionParser].
 */
class SpdxExpressionLexer(input: Sequence<Char>) {
    constructor(input: String) : this(input.asSequence())

    companion object {
        /** The uppercase characters allowed by the SPDX specification. */
        private val UPPERCASE = 'A'..'Z'

        /** The lowercase characters allowed by the SPDX specification. */
        private val LOWERCASE = 'a'..'z'

        /** The digits allowed by the SPDX specification. */
        private val DIGITS = '0'..'9'

        /** Return true if the character is an uppercase character allowed by the SPDX specification. */
        private fun Char.isUpper() = this in UPPERCASE

        /** Return true if the character is a lowercase character allowed by the SPDX specification. */
        private fun Char.isLower() = this in LOWERCASE

        /** Return true if the character is a digit allowed by the SPDX specification. */
        private fun Char.isDigit() = this in DIGITS

        /** Return true if the character is a valid identifier character allowed by the SPDX specification. */
        private fun Char.isIdentifier() = isUpper() || isLower() || isDigit() || this == '.' || this == '-'
    }

    private val iterator = input.iterator()
    private var next = iterator.nextOrNull()
    private var position = 0

    fun tokens(): Sequence<Token> = generateSequence { nextToken() }

    private fun nextToken(): Token? {
        var cur = consumeChar()

        while (cur != null) {
            if (cur == ' ') {
                cur = consumeChar()
                continue
            }

            if (cur == '(') return Token.OPEN(position)
            if (cur == ')') return Token.CLOSE(position)
            if (cur == '+') return Token.PLUS(position)
            if (cur == ':') return Token.COLON(position)

            if (cur.isIdentifier()) {
                val start = position
                val value = buildString {
                    append(cur)

                    while (next?.isIdentifier() == true) {
                        cur = consumeChar()
                        append(cur)
                    }
                }

                return when (value.uppercase()) {
                    "AND" -> Token.AND(start)
                    "OR" -> Token.OR(start)
                    "WITH" -> Token.WITH(start)
                    else -> when {
                        value.startsWith("DocumentRef-") -> Token.DOCUMENTREF(start, position, value)
                        value.startsWith("LicenseRef-") -> Token.LICENSEREF(start, position, value)
                        else -> Token.IDENTIFIER(start, position, value)
                    }
                }
            }

            throw SpdxExpressionLexerException(cur, position)
        }

        return null
    }

    private fun consumeChar(): Char? =
        next?.also {
            position++
            next = iterator.nextOrNull()
        }
}
