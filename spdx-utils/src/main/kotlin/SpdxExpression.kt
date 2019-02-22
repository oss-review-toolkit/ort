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
    /**
     * The level of strictness to apply when validating an [SpdxExpression].
     */
    enum class Strictness {
        /**
         * Any license identifier string is leniently allowed.
         */
        ALLOW_ANY,

        /**
         * All SPDX license identifier strings are allowed, including deprecated ones.
         */
        ALLOW_DEPRECATED,

        /**
         * Only current SPDX license identifier strings are allowed, excluding deprecated ones.
         */
        ALLOW_CURRENT
    }

    companion object {
        /**
         * Parse a string into an [SpdxExpression]. Parsing only checks the syntax and the individual license
         * expressions may be invalid SPDX identifiers, which is useful to parse expressions with non-SPDX declared
         * licenses. Throws an [SpdxException] if the string cannot be parsed.
         */
        @JsonCreator
        @JvmStatic
        fun parse(expression: String) = parse(expression, Strictness.ALLOW_ANY)

        /**
         * Parse a string into an [SpdxExpression]. [strictness] defines whether only the syntax is checked
         * ([ALLOW_ANY][Strictness.ALLOW_ANY]), or semantics are also checked but deprecated license identifiers are
         * allowed ([ALLOW_DEPRECATED][Strictness.ALLOW_DEPRECATED]) or only current license identifiers are allowed
         * ([ALLOW_CURRENT][Strictness.ALLOW_CURRENT]). Throws an [SpdxException] if the string cannot be parsed.
         */
        fun parse(expression: String, strictness: Strictness): SpdxExpression {
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
            val visitor = SpdxExpressionDefaultVisitor(strictness)

            return visitor.visit(parser.licenseExpression())
        }
    }

    /**
     * Return all license IDs contained in this expression. Non-SPDX licenses and SPDX license references are included.
     */
    abstract fun licenses(): List<String>

    /**
     * Return all [SpdxLicense]s contained in this expression. Non-SPDX licenses and SPDX license references are
     * ignored.
     */
    abstract fun spdxLicenses(): EnumSet<SpdxLicense>

    /**
     * Normalize all license IDs using a mapping containing common misspellings of license IDs. If [mapDeprecated] is
     * `true` also deprecated IDs are mapped to they current counterparts. The result of this function is not guaranteed
     * to contain only valid IDs. Use [validate] to check the returned [SpdxExpression] for validity afterwards.
     */
    abstract fun normalize(mapDeprecated: Boolean = true): SpdxExpression

    /**
     * Validate this expression. [strictness] defines whether only the syntax is checked
     * ([ALLOW_ANY][Strictness.ALLOW_ANY]), or semantics are also checked but deprecated license identifiers are
     * allowed ([ALLOW_DEPRECATED][Strictness.ALLOW_DEPRECATED]) or only current license identifiers are allowed
     * ([ALLOW_CURRENT][Strictness.ALLOW_CURRENT]). Throws an [SpdxException] if validation fails.
     */
    abstract fun validate(strictness: Strictness)

    /**
     * Concatenate [this][SpdxExpression] and [other] using [SpdxOperator.AND].
     */
    infix fun and(other: SpdxExpression) = SpdxCompoundExpression(this, SpdxOperator.AND, other)

    /**
     * Concatenate [this][SpdxExpression] and [other] using [SpdxOperator.OR].
     */
    infix fun or(other: SpdxExpression) = SpdxCompoundExpression(this, SpdxOperator.OR, other)
}

/**
 * An SPDX expression compound of two expressions with an operator.
 */
data class SpdxCompoundExpression(
        val left: SpdxExpression,
        val operator: SpdxOperator,
        val right: SpdxExpression
) : SpdxExpression() {
    override fun licenses() = left.licenses() + right.licenses()

    override fun spdxLicenses() = left.spdxLicenses() + right.spdxLicenses()

    override fun normalize(mapDeprecated: Boolean) =
            SpdxCompoundExpression(left.normalize(mapDeprecated), operator, right.normalize(mapDeprecated))

    override fun validate(strictness: Strictness) {
        left.validate(strictness)

        if (operator == SpdxOperator.WITH && right !is SpdxLicenseExceptionExpression) {
            throw SpdxException("Argument '$right' for WITH is not an SPDX license exception id.")
        }

        if (operator != SpdxOperator.WITH && right is SpdxLicenseExceptionExpression) {
            throw SpdxException("Argument '$right' for $operator must not be an SPDX license exception id.")
        }

        right.validate(strictness)
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
    override fun licenses() = emptyList<String>()

    override fun spdxLicenses() = enumSetOf<SpdxLicense>()

    override fun normalize(mapDeprecated: Boolean) = this

    override fun validate(strictness: Strictness) {
        val licenseException = SpdxLicenseException.forId(id)
        when (strictness) {
            Strictness.ALLOW_ANY -> id // Return something non-null.
            Strictness.ALLOW_DEPRECATED -> licenseException
            Strictness.ALLOW_CURRENT -> licenseException?.takeUnless { licenseException.deprecated }
        } ?: throw SpdxException("'$id' is not a valid SPDX license exception id.")
    }

    override fun toString() = id
}

/**
 * An SPDX expression for a license id as defined by version 2.1 of the SPDX specification, appendix I, see
 * https://spdx.org/spdx-specification-21-web-version#h.luq9dgcle9mo.
 */
data class SpdxLicenseIdExpression(
        val id: String,
        val orLaterVersion: Boolean = false
) : SpdxExpression() {
    private val spdxLicense = SpdxLicense.forId(toString())

    override fun licenses() = listOf(toString())

    override fun spdxLicenses() = spdxLicense?.let { enumSetOf(it) } ?: enumSetOf()

    override fun normalize(mapDeprecated: Boolean) =
            SpdxLicenseAliasMapping.map(toString(), mapDeprecated) ?: this

    override fun validate(strictness: Strictness) {
        when (strictness) {
            Strictness.ALLOW_ANY -> Unit // Return something non-null.
            Strictness.ALLOW_DEPRECATED -> spdxLicense
            Strictness.ALLOW_CURRENT -> spdxLicense?.takeUnless { spdxLicense.deprecated }
        } ?: throw SpdxException("'$this' is not a valid SPDX license id.")
    }

    /**
     * Concatenate [this][SpdxLicenseIdExpression] and [other] using [SpdxOperator.WITH].
     */
    infix fun with(other: SpdxLicenseExceptionExpression) = SpdxCompoundExpression(this, SpdxOperator.WITH, other)

    override fun toString() =
            buildString {
                append(id)
                // While in the current SPDX standard the "or later version" semantic is part of the id string itself,
                // it is a generic "+" operator for deprecated licenses.
                if (orLaterVersion && !id.endsWith("-or-later")) append("+")
            }
}

/**
 * An SPDX expression for a license reference as defined by version 2.1 of the SPDX specification, appendix IV, see
 * https://spdx.org/spdx-specification-21-web-version#h.jxpfx0ykyb60.
 */
data class SpdxLicenseReferenceExpression(
        val id: String
) : SpdxExpression() {
    override fun licenses() = listOf(id)

    override fun spdxLicenses() = enumSetOf<SpdxLicense>()

    override fun normalize(mapDeprecated: Boolean) = this

    override fun validate(strictness: Strictness) {
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
