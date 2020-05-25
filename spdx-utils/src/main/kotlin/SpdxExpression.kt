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

package org.ossreviewtoolkit.spdx

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

/**
 * An SPDX expression as defined by version 2.1 of the [SPDX specification, appendix IV][1].
 *
 * [1]: https://spdx.org/spdx-specification-21-web-version#h.jxpfx0ykyb60
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
         * The "WITH" keyword, used to concatenate a license with an exception.
         */
        const val WITH = "WITH"

        /**
         * Parse a string into an [SpdxExpression]. Parsing only checks the syntax and the individual license
         * expressions may be invalid SPDX identifiers, which is useful to parse expressions with non-SPDX declared
         * licenses. Throws an [SpdxException] if the string cannot be parsed.
         */
        @JsonCreator
        @JvmStatic
        fun parse(expression: String): SpdxExpression = parse(expression, Strictness.ALLOW_ANY)

        /**
         * Parse a string into an [SpdxExpression]. [strictness] defines whether only the syntax is checked
         * ([ALLOW_ANY][Strictness.ALLOW_ANY]), or semantics are also checked but deprecated license identifiers are
         * allowed ([ALLOW_DEPRECATED][Strictness.ALLOW_DEPRECATED]) or only current license identifiers are allowed
         * ([ALLOW_CURRENT][Strictness.ALLOW_CURRENT]). Throws an [SpdxException] if the string cannot be parsed.
         */
        fun parse(expression: String, strictness: Strictness): SpdxExpression {
            val charStream = CharStreams.fromString(expression)
            val lexer = SpdxExpressionLexer(charStream).apply {
                removeErrorListeners()
                addErrorListener(SpdxErrorListener())
            }

            val tokenStream = CommonTokenStream(lexer)
            val parser = SpdxExpressionParser(tokenStream).apply {
                removeErrorListeners()
                addErrorListener(SpdxErrorListener())
            }

            return SpdxExpressionDefaultVisitor(strictness).visit(parser.licenseExpression())
        }
    }

    /**
     * Return all single licenses contained in this expression as list of [SpdxSingleLicenseExpression]s.
     */
    abstract fun decompose(): Set<SpdxSingleLicenseExpression>

    /**
     * Return the [disjunctive normal form][1] of this expression.
     *
     * [1]: https://en.wikipedia.org/wiki/Disjunctive_normal_form
     */
    open fun disjunctiveNormalForm(): SpdxExpression = this

    /**
     * Return all license IDs contained in this expression. Non-SPDX licenses and SPDX license references are included.
     */
    abstract fun licenses(): List<String>

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
     * Return all valid license choices for this SPDX expression, by converting it to the
     * [disjunctive normal form][disjunctiveNormalForm] and collecting all disjunct expressions.
     */
    fun validChoices(): Set<SpdxExpression> = disjunctiveNormalForm().validChoicesForDnf()

    /**
     * Internal implementation of [validChoices], assuming that this expression is already in disjunctive normal form.
     */
    protected open fun validChoicesForDnf(): Set<SpdxExpression> = setOf(this)

    /**
     * Return if this expression is valid according to the [strictness]. Also see [validate].
     */
    fun isValid(strictness: Strictness = Strictness.ALLOW_CURRENT): Boolean =
        runCatching { validate(strictness) }.isSuccess

    /**
     * Return true if this expression offers a license choice. This can only be true if this expression contains the
     * [OR operator][SpdxOperator.OR].
     */
    open fun offersChoice(): Boolean = false

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
 * An SPDX expression compound of a [left] and a [right] expression with an [operator] as defined by version 2.1 of the
 * [SPDX specification, appendix IV][1].
 *
 * [1]: https://spdx.org/spdx-specification-21-web-version#h.jxpfx0ykyb60
 */
data class SpdxCompoundExpression(
    val left: SpdxExpression,
    val operator: SpdxOperator,
    val right: SpdxExpression
) : SpdxExpression() {
    override fun decompose() = left.decompose() + right.decompose()

    override fun disjunctiveNormalForm(): SpdxExpression {
        val leftDnf = left.disjunctiveNormalForm()
        val rightDnf = right.disjunctiveNormalForm()

        return when (operator) {
            SpdxOperator.OR -> SpdxCompoundExpression(leftDnf, SpdxOperator.OR, rightDnf)

            SpdxOperator.AND -> when {
                leftDnf is SpdxCompoundExpression && leftDnf.operator == SpdxOperator.OR &&
                        rightDnf is SpdxCompoundExpression && rightDnf.operator == SpdxOperator.OR ->
                    ((leftDnf.left and rightDnf.left) or (leftDnf.left and rightDnf.right)) or
                            ((leftDnf.right and rightDnf.left) or (leftDnf.right and rightDnf.right))

                leftDnf is SpdxCompoundExpression && leftDnf.operator == SpdxOperator.OR ->
                    (leftDnf.left and rightDnf) or (leftDnf.right and rightDnf)

                rightDnf is SpdxCompoundExpression && rightDnf.operator == SpdxOperator.OR ->
                    (leftDnf and rightDnf.left) or (leftDnf and rightDnf.right)

                else -> SpdxCompoundExpression(leftDnf, operator, rightDnf)
            }
        }
    }

    override fun licenses() = left.licenses() + right.licenses()

    override fun normalize(mapDeprecated: Boolean) =
        SpdxCompoundExpression(left.normalize(mapDeprecated), operator, right.normalize(mapDeprecated))

    override fun validate(strictness: Strictness) {
        left.validate(strictness)
        right.validate(strictness)
    }

    override fun validChoicesForDnf(): Set<SpdxExpression> =
        when (operator) {
            SpdxOperator.AND -> setOf(this)

            SpdxOperator.OR -> {
                val validChoicesLeft = when (left) {
                    is SpdxCompoundExpression -> left.validChoicesForDnf()
                    else -> left.validChoices()
                }

                val validChoicesRight = when (right) {
                    is SpdxCompoundExpression -> right.validChoicesForDnf()
                    else -> right.validChoices()
                }

                validChoicesLeft + validChoicesRight
            }
        }

    override fun offersChoice(): Boolean =
        when (operator) {
            SpdxOperator.OR -> true
            SpdxOperator.AND -> left.offersChoice() || right.offersChoice()
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
 * An SPDX expression that contains only a single license with an optional exception. Can be
 * [SpdxLicenseWithExceptionExpression] or any subtype of [SpdxSimpleExpression].
 */
sealed class SpdxSingleLicenseExpression : SpdxExpression()

/**
 * An SPDX expression that contains a [license] with an [exception].
 */
data class SpdxLicenseWithExceptionExpression(
    val license: SpdxSimpleExpression,
    val exception: String
) : SpdxSingleLicenseExpression() {
    override fun decompose() = setOf(this)

    override fun licenses() = license.licenses()

    override fun normalize(mapDeprecated: Boolean): SpdxExpression {
        // Manually cast to SpdxLicenseException, because the type resolver does not recognize that in all subclasses of
        // SpdxSimpleExpression normalize() returns an SpdxSingleLicenseExpression.
        val normalizedLicense = license.normalize(mapDeprecated) as SpdxSingleLicenseExpression

        return when (normalizedLicense) {
            is SpdxSimpleExpression -> SpdxLicenseWithExceptionExpression(normalizedLicense, exception)

            // This case happens if a deprecated license identifier that contains an exception is used together with
            // another exception, for example "GPL-2.0-with-classpath-exception WITH Classpath-exception-2.0". If the
            // exceptions are equal ignore this issue, otherwise throw an exception.
            is SpdxLicenseWithExceptionExpression -> {
                if (normalizedLicense.exception == exception) {
                    normalizedLicense
                } else {
                    throw SpdxException(
                        "'$this' cannot be normalized, because the license '$license' contains the exception " +
                                "'${normalizedLicense.exception}' which is different from '$exception'."
                    )
                }
            }
        }
    }

    override fun validate(strictness: Strictness) {
        license.validate(strictness)

        val spdxException = SpdxLicenseException.forId(exception)
        when (strictness) {
            Strictness.ALLOW_ANY -> exception // Return something non-null.
            Strictness.ALLOW_DEPRECATED -> spdxException
            Strictness.ALLOW_CURRENT -> spdxException?.takeUnless { spdxException.deprecated }
        } ?: throw SpdxException("'$exception' is not a valid SPDX license exception id.")
    }

    override fun toString(): String = "$license $WITH $exception"
}

/**
 * A simple SPDX expression as defined by version 2.1 of the [SPDX specification, appendix IV][1]. A simple expression
 * can be either a [SpdxLicenseIdExpression] or a [SpdxLicenseReferenceExpression].
 *
 * [1]: https://spdx.org/spdx-specification-21-web-version#h.jxpfx0ykyb60
 */
sealed class SpdxSimpleExpression : SpdxSingleLicenseExpression() {
    /**
     * Concatenate [this][SpdxSimpleExpression] and [other] using [SpdxExpression.WITH].
     */
    infix fun with(other: String) = SpdxLicenseWithExceptionExpression(this, other)
}

/**
 * An SPDX expression for a license [id] as defined by version 2.1 of the [SPDX specification, appendix I][1].
 * [orLaterVersion] indicates whether the license id also describes later versions of the license.
 *
 * [1]: https://spdx.org/spdx-specification-21-web-version#h.luq9dgcle9mo
 */
data class SpdxLicenseIdExpression(
    val id: String,
    val orLaterVersion: Boolean = false
) : SpdxSimpleExpression() {
    override fun decompose() = setOf(this)

    private val spdxLicense = SpdxLicense.forId(toString())

    override fun licenses() = listOf(toString())

    override fun normalize(mapDeprecated: Boolean) =
        SpdxLicenseAliasMapping.map(toString(), mapDeprecated) ?: this

    override fun validate(strictness: Strictness) {
        when (strictness) {
            Strictness.ALLOW_ANY -> Unit // Return something non-null.
            Strictness.ALLOW_DEPRECATED -> spdxLicense
            Strictness.ALLOW_CURRENT -> spdxLicense?.takeUnless { spdxLicense.deprecated }
        } ?: throw SpdxException("'$this' is not a valid SPDX license id.")
    }

    override fun toString() =
        buildString {
            append(id)
            // While in the current SPDX standard the "or later version" semantic is part of the id string itself,
            // it is a generic "+" operator for deprecated licenses.
            if (orLaterVersion && !id.endsWith("-or-later")) append("+")
        }
}

/**
 * An SPDX expression for a license reference [id] as defined by version 2.1 of the
 * [SPDX specification, appendix IV][1].
 *
 * [1]: https://spdx.org/spdx-specification-21-web-version#h.jxpfx0ykyb60
 */
data class SpdxLicenseReferenceExpression(
    val id: String
) : SpdxSimpleExpression() {
    override fun decompose() = setOf(this)

    override fun licenses() = listOf(id)

    override fun normalize(mapDeprecated: Boolean) = this

    override fun validate(strictness: Strictness) {
        if (!(id.startsWith("LicenseRef-") ||
                    (id.startsWith("DocumentRef-") && id.contains(":LicenseRef-")))
        ) {
            throw SpdxException("'$id' is not an SPDX license reference.")
        }
    }

    override fun toString() = id
}

/**
 * An SPDX operator for use in compound expressions as defined by version 2.1 of the
 * [SPDX specification, appendix IV][1].
 *
 * [1]: https://spdx.org/spdx-specification-21-web-version#h.jxpfx0ykyb60
 */
enum class SpdxOperator(
    /**
     * The priority of the operator. An operator with a larger priority value binds stronger than an operator with a
     * lower priority value. Operators with the same priority bind left-associative.
     */
    val priority: Int
) {
    /**
     * The conjunctive binary "AND" operator to construct a new license expression if required to simultaneously comply
     * with two or more licenses, where both the left and right operands are valid [SpdxExpressions][SpdxExpression].
     */
    AND(1),

    /**
     * The disjunctive binary "OR" operator to construct a new license expression if presented with a choice between
     * two or more licenses, where both the left and right operands are valid [SpdxExpressions][SpdxExpression].
     */
    OR(0)
}
