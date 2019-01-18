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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer

import java.util.EnumSet

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

/**
 * An SPDX expression as defined by version 2.1 of the SPDX specification, appendix IV, see
 * https://spdx.org/spdx-specification-21-web-version#h.jxpfx0ykyb60.
 */
@JsonSerialize(using = ToStringSerializer::class)
sealed class SpdxExpression {
    companion object {
        /**
         * Parse a string into an [SpdxExpression]. Throws an [SpdxException] if the string cannot be parsed.
         */
        @JsonCreator
        @JvmStatic
        fun parse(expression: String): SpdxExpression {
            val charStream = CharStreams.fromString(expression)
            val lexer = SpdxExpressionLexer(charStream)

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

            val tokenStream = CommonTokenStream(lexer)
            val parser = SpdxExpressionParser(tokenStream)
            val visitor = SpdxExpressionDefaultVisitor()

            return visitor.visit(parser.licenseExpression())
        }
    }

    /**
     * Return all [SpdxLicense]s contained in this expression. Non-SPDX licenses and SPDX license references are
     * ignored.
     */
    abstract fun spdxLicenses(): EnumSet<SpdxLicense>

    /**
     * Validate this expression to only contain SPDX identifiers. This includes SPDX licenses, exceptions and license
     * references. Throws an [SpdxException] if validation fails.
     */
    abstract fun validate()
}

/**
 * An SPDX expression compound of two expressions with an operator.
 */
data class SpdxCompoundExpression(
        val left: SpdxExpression,
        val operator: SpdxOperator,
        val right: SpdxExpression
) : SpdxExpression() {
    override fun spdxLicenses() = left.spdxLicenses() + right.spdxLicenses()

    override fun validate() {
        left.validate()

        if (operator == SpdxOperator.WITH && right !is SpdxLicenseExceptionExpression) {
            throw SpdxException("Argument '$right' for WITH is not an SPDX license exception id.")
        }

        if (operator != SpdxOperator.WITH && right is SpdxLicenseExceptionExpression) {
            throw SpdxException("Argument '$right' for $operator must not be an SPDX license exception id.")
        }

        right.validate()
    }

    override fun toString(): String {
        // If the priority of this operator is higher than the binding of the left or right operator, we need to put the
        // left or right expressions in parenthesis to not change the semantics of the expression.
        val leftString = when {
            left is SpdxCompoundExpression && operator.priority > left.operator.priority -> "($left)"
            else -> "$left"
        }
        val rightString = when {
            right is SpdxCompoundExpression && operator.priority > right.operator.priority -> "($right)"
            else -> "$right"
        }

        return "$leftString $operator $rightString"
    }
}

/**
 * An SPDX expression for a license exception as defined by version 2.1 of the SPDX specification, appendix I, see
 * https://spdx.org/spdx-specification-21-web-version#h.ruv3yl8g6czd.
 */
data class SpdxLicenseExceptionExpression(
        val id: String
) : SpdxExpression() {
    override fun spdxLicenses() = enumSetOf<SpdxLicense>()

    override fun validate() {
        SpdxLicenseException.forId(id) ?: throw SpdxException("'$id' is not an SPDX license exception id.")
    }

    override fun toString() = id
}

/**
 * An SPDX expression for a license id as defined by version 2.1 of the SPDX specification, appendix I, see
 * https://spdx.org/spdx-specification-21-web-version#h.luq9dgcle9mo.
 */
data class SpdxLicenseIdExpression(
        val id: String,
        val anyLaterVersion: Boolean = false
) : SpdxExpression() {
    override fun spdxLicenses() = SpdxLicense.forId(id)?.let { enumSetOf(it) } ?: enumSetOf()

    override fun validate() {
        SpdxLicense.forId(id) ?: throw SpdxException("'$id' is not an SPDX license id.")

        if (anyLaterVersion && !(id.startsWith("GPL-") || id.startsWith("LGPL-"))) {
            throw SpdxException("The + operator is only allowed for GPL and LGPL licenses.")
        }
    }

    override fun toString() =
            buildString {
                append(id)
                if (anyLaterVersion) append("+")
            }
}

/**
 * An SPDX expression for a license reference as defined by version 2.1 of the SPDX specification, appendix IV, see
 * https://spdx.org/spdx-specification-21-web-version#h.jxpfx0ykyb60.
 */
data class SpdxLicenseReferenceExpression(
        val id: String
) : SpdxExpression() {
    override fun spdxLicenses() = enumSetOf<SpdxLicense>()

    override fun validate() {
        if (!(id.startsWith("LicenseRef-") ||
                (id.startsWith("DocumentRef-") && id.contains(":LicenseRef-")))) {
            throw SpdxException("'$id' is not an SPDX license reference.")
        }
    }

    override fun toString() = id
}

/**
 * An SPDX operator for use in compound expressions as defined by version 2.1 of the SPDX specification, appendix IV,
 * see https://spdx.org/spdx-specification-21-web-version#h.jxpfx0ykyb60.
 */
enum class SpdxOperator(
        /**
         * The priority of the operator. An operator with a larger priority value binds stronger than an operator with a
         * lower priority value. Operators with the same priority bind left-associative.
         */
        val priority: Int
) {
    AND(1),
    OR(0),
    WITH(2)
}
