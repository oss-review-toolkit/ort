/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

import org.ossreviewtoolkit.utils.spdx.SpdxConstants.DOCUMENT_REF_PREFIX
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.LICENSE_REF_PREFIX
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice

/**
 * An SPDX expression as defined by version 2.1 of the [SPDX specification, appendix IV][1].
 *
 * [1]: https://spdx.dev/spdx-specification-21-web-version#h.jxpfx0ykyb60
 */
@JsonSerialize(using = ToStringSerializer::class)
sealed class SpdxExpression {
    /**
     * The level of strictness to apply when validating an [SpdxExpression].
     */
    enum class Strictness {
        /**
         * Any license identifier string is leniently allowed. The expression is not limited to SPDX license identifier
         * strings or LicenseRefs.
         */
        ALLOW_ANY,

        /**
         * All SPDX license identifier strings, including deprecated ones, and LicenseRefs are allowed. Arbitrary
         * license identifier strings are not allowed.
         */
        ALLOW_DEPRECATED,

        /**
         * Only current SPDX license identifier strings and LicenseRefs are allowed. This excludes deprecated SPDX
         * license identifier strings and arbitrary license identifier strings.
         */
        ALLOW_CURRENT,

        /**
         * This is the same as [ALLOW_CURRENT], but additionally allows LicenseRefs that contain the "exception" string
         * to be used as license exceptions after the [WITH] operator.
         */
        ALLOW_LICENSEREF_EXCEPTIONS
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
     * Return all licenses contained in this expression as set of [SpdxSingleLicenseExpression]s, where compound SPDX
     * license expressions are split, omitting the operator. The individual string representations of the returned
     * license expressions may contain spaces as they might contain [SpdxLicenseWithExceptionExpression]s.
     */
    abstract fun decompose(): Set<SpdxSingleLicenseExpression>

    /**
     * Return all licenses contained in this expression as a list of strings, where compound SPDX license expressions
     * are split, omitting the operator, and exceptions to licenses are dropped. As a result, the returned strings do
     * not contain any spaces each.
     */
    abstract fun licenses(): List<String>

    /**
     * Return the [disjunctive normal form][1] of this expression.
     *
     * [1]: https://en.wikipedia.org/wiki/Disjunctive_normal_form
     */
    open fun disjunctiveNormalForm(): SpdxExpression = this

    /**
     * Normalize all license IDs using a mapping containing common misspellings of license IDs. If [mapDeprecated] is
     * `true`, also deprecated IDs are mapped to their current counterparts. The result of this function is not
     * guaranteed to contain only valid IDs. Use [validate] to check the returned [SpdxExpression] for validity
     * afterwards.
     */
    abstract fun normalize(mapDeprecated: Boolean = true): SpdxExpression

    /**
     * Sort this expression lexicographically.
     */
    open fun sort(): SpdxExpression = this

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
     * Return whether this expression contains [present][SpdxConstants.isPresent] licenses, i.e. not all licenses in
     * this expression are "not present" values.
     */
    fun isPresent() = licenses().any { SpdxConstants.isPresent(it) }

    /**
     * Return if this expression is valid according to the [strictness]. Also see [validate].
     */
    fun isValid(strictness: Strictness = Strictness.ALLOW_CURRENT): Boolean =
        runCatching { validate(strictness) }.isSuccess

    /**
     * Return true if [choice] is a valid license choice for this SPDX expression. This can only be the case, if
     * [choice] does not offer a license choice itself and if the [licenses][SpdxSingleLicenseExpression] contained in
     * [choice] match any of the [valid license choices][validChoices].
     */
    fun isValidChoice(choice: SpdxExpression): Boolean = !choice.offersChoice() && choice in validChoices()

    /**
     * Return true if [subExpression] is a valid sub-expression of [this][SpdxExpression].
     */
    open fun isSubExpression(subExpression: SpdxExpression?): Boolean = false

    /**
     * Return true if this expression offers a license choice. This can only be true if this expression contains the
     * [OR operator][SpdxOperator.OR].
     */
    open fun offersChoice(): Boolean = false

    /**
     * Apply a license [choice], optionally limited to the given [subExpression], and return a [SpdxExpression] where
     * the choice is resolved.
     */
    open fun applyChoice(choice: SpdxExpression, subExpression: SpdxExpression = this): SpdxExpression {
        if (this != subExpression) {
            throw InvalidSubExpressionException("$subExpression is not a valid subExpression for $this")
        }

        if (this != choice) {
            throw InvalidLicenseChoiceException("Cannot select $choice for expression $this.")
        }

        return this
    }

    /**
     * Apply [licenseChoices] in the given order to [this][SpdxExpression].
     */
    fun applyChoices(licenseChoices: List<SpdxLicenseChoice>): SpdxExpression {
        if (validChoices().size == 1) return this

        var currentExpression = this

        licenseChoices.forEach {
            if (it.given == null && currentExpression.isValidChoice(it.choice)) {
                currentExpression = currentExpression.applyChoice(it.choice)
            } else if (currentExpression.isSubExpression(it.given)) {
                currentExpression = currentExpression.applyChoice(it.choice, it.given!!)
            }
        }

        return currentExpression
    }

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
 * [1]: https://spdx.dev/spdx-specification-21-web-version#h.jxpfx0ykyb60
 */
class SpdxCompoundExpression(
    val left: SpdxExpression,
    val operator: SpdxOperator,
    val right: SpdxExpression
) : SpdxExpression() {
    override fun decompose() = left.decompose() + right.decompose()

    override fun licenses() = left.licenses() + right.licenses()

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

    override fun normalize(mapDeprecated: Boolean) =
        SpdxCompoundExpression(left.normalize(mapDeprecated), operator, right.normalize(mapDeprecated))

    override fun sort(): SpdxExpression {
        /**
         * Get all transitive children of this expression that are concatenated with the same operator as this compound
         * expression. These can be re-ordered because the AND and OR operators are both commutative.
         */
        fun getSortedChildrenWithSameOperator(expression: SpdxCompoundExpression): List<SpdxExpression> {
            val children = mutableListOf<SpdxExpression>()

            fun addChildren(child: SpdxExpression) {
                if (child is SpdxCompoundExpression && child.operator == operator) {
                    children += getSortedChildrenWithSameOperator(child)
                } else {
                    children += child.sort()
                }
            }

            addChildren(expression.left)
            addChildren(expression.right)

            return children.sortedBy { it.toString() }
        }

        return getSortedChildrenWithSameOperator(this).reduce(
            when (operator) {
                SpdxOperator.AND -> SpdxExpression::and
                SpdxOperator.OR -> SpdxExpression::or
            }
        )
    }

    override fun validate(strictness: Strictness) {
        left.validate(strictness)
        right.validate(strictness)
    }

    override fun validChoicesForDnf(): Set<SpdxExpression> =
        when (operator) {
            SpdxOperator.AND -> setOf(decompose().reduce(SpdxExpression::and))

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

    override fun applyChoice(choice: SpdxExpression, subExpression: SpdxExpression): SpdxExpression {
        if (!subExpression.validChoices().containsAll(choice.validChoices())) {
            throw InvalidLicenseChoiceException(
                "$choice is not a valid choice for $subExpression. Valid choices are: ${validChoices()}."
            )
        }

        if (!isSubExpression(subExpression)) {
            throw InvalidSubExpressionException("$subExpression is not not a valid subExpression of $this")
        }

        return replaceSubexpressionWithChoice(subExpression, choice)
    }

    private fun replaceSubexpressionWithChoice(subExpression: SpdxExpression, choice: SpdxExpression): SpdxExpression {
        val expressionString = toString()
        val subExpressionString = subExpression.toString()
        val choiceString = choice.toString()

        return if (subExpressionString in expressionString) {
            expressionString.replace(subExpressionString, choiceString).toSpdx()
        } else {
            val dismissedLicense = subExpression.validChoices().first { it != choice }
            val unchosenLicenses = validChoices().filter { it != dismissedLicense }

            if (unchosenLicenses.isEmpty()) {
                throw IllegalArgumentException("No licenses left after applying choice $choice to $subExpression")
            } else {
                unchosenLicenses.reduce(SpdxExpression::or)
            }
        }
    }

    override fun isSubExpression(subExpression: SpdxExpression?): Boolean {
        if (subExpression == null) return false

        val expressionString = toString()
        val subExpressionString = subExpression.toString()

        return validChoices().containsAll(subExpression.validChoices()) || subExpressionString in expressionString
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SpdxExpression) return false

        val validChoices = validChoices().map { it.decompose() }
        val otherValidChoices = other.validChoices().map { it.decompose() }

        return validChoices.size == otherValidChoices.size && validChoices.all { it in otherValidChoices }
    }

    override fun hashCode() = decompose().sumOf { it.hashCode() }

    override fun toString() =
        // If the operator of the left or right expression is different from the operator of this expression, put the
        // respective expression in parentheses. Semantically this would only be required if the priority of this
        // operator is higher than the priority of the operator of the left or right expression, but always adding
        // parentheses makes it easier to understand the expression.
        buildString {
            when {
                left is SpdxCompoundExpression && operator != left.operator -> append("($left)")
                else -> append("$left")
            }

            append(" $operator ")

            when {
                right is SpdxCompoundExpression && operator != right.operator -> append("($right)")
                else -> append("$right")
            }
        }
}

/**
 * An SPDX expression that contains only a single license with an optional exception. Can be
 * [SpdxLicenseWithExceptionExpression] or any subtype of [SpdxSimpleExpression].
 */
sealed class SpdxSingleLicenseExpression : SpdxExpression() {
    companion object {
        /**
         * Parse a string into an [SpdxSingleLicenseExpression]. Throws an [SpdxException] if the string cannot be
         * parsed. Throws a [ClassCastException] if the string is an [SpdxCompoundExpression].
         */
        @JsonCreator
        @JvmStatic
        fun parse(expression: String): SpdxSingleLicenseExpression =
            SpdxExpression.parse(expression) as SpdxSingleLicenseExpression
    }

    /**
     * Return the string identifier of this license without any license exception.
     */
    abstract fun simpleLicense(): String

    /**
     * Return the license exception identifier if this is a [SpdxLicenseWithExceptionExpression] or null otherwise.
     */
    abstract fun exception(): String?

    /**
     * Return the URL for the licence if this is [SpdxLicenseIdExpression] or [SpdxLicenseWithExceptionExpression].
     * Otherwise, return null.
     */
    abstract fun getLicenseUrl(): String?
}

/**
 * An SPDX expression that contains a [license] with an [exception].
 */
class SpdxLicenseWithExceptionExpression(
    val license: SpdxSimpleExpression,
    val exception: String
) : SpdxSingleLicenseExpression() {
    companion object {
        private val EXCEPTION_STRING_PATTERN = Regex("\\bexception\\b", RegexOption.IGNORE_CASE)

        /**
         * Parse a string into an [SpdxLicenseWithExceptionExpression]. Throws an [SpdxException] if the string cannot
         * be parsed. Throws a [ClassCastException] if the string is not an [SpdxLicenseWithExceptionExpression].
         */
        @JsonCreator
        @JvmStatic
        fun parse(expression: String): SpdxLicenseWithExceptionExpression =
            SpdxExpression.parse(expression) as SpdxLicenseWithExceptionExpression
    }

    override fun decompose() = setOf(this)

    override fun licenses() = license.licenses()

    override fun simpleLicense() = license.toString()

    override fun exception() = exception

    override fun normalize(mapDeprecated: Boolean) =
        // Manually cast to SpdxSingleLicenseExpression as the type resolver does not recognize that in all subclasses
        // of SpdxSimpleExpression normalize() returns an SpdxSingleLicenseExpression.
        when (val normalizedLicense = license.normalize(mapDeprecated) as SpdxSingleLicenseExpression) {
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

    override fun validate(strictness: Strictness) {
        license.validate(strictness)

        // Return early without any further checks on the exception in lenient mode.
        if (strictness == Strictness.ALLOW_ANY) return

        val spdxLicenseException = SpdxLicenseException.forId(exception)
        if (strictness == Strictness.ALLOW_DEPRECATED && spdxLicenseException != null) return

        val isCurrentLicenseException = spdxLicenseException?.deprecated == false
        if (strictness == Strictness.ALLOW_CURRENT && isCurrentLicenseException) return

        if (strictness == Strictness.ALLOW_LICENSEREF_EXCEPTIONS) {
            val isValidLicenseRef = SpdxLicenseReferenceExpression(exception).isValid(strictness)
            val isExceptionString = exception.contains(EXCEPTION_STRING_PATTERN)
            if (isCurrentLicenseException || (isValidLicenseRef && isExceptionString)) return
        }

        throw SpdxException("'$exception' is not a valid SPDX license exception string.")
    }

    override fun equals(other: Any?) =
        when (other) {
            is SpdxLicenseWithExceptionExpression -> license == other.license && exception == other.exception

            is SpdxExpression -> {
                val decomposed = other.decompose()
                decomposed.size == 1 && decomposed.first().let {
                    it is SpdxLicenseWithExceptionExpression && it.license == license && it.exception == exception
                }
            }

            else -> false
        }

    override fun hashCode() = license.hashCode() + 31 * exception.hashCode()

    override fun toString(): String = "$license $WITH $exception"

    override fun getLicenseUrl() = license.getLicenseUrl()
}

/**
 * A simple SPDX expression as defined by version 2.1 of the [SPDX specification, appendix IV][1]. A simple expression
 * can be either a [SpdxLicenseIdExpression] or a [SpdxLicenseReferenceExpression].
 *
 * [1]: https://spdx.dev/spdx-specification-21-web-version#h.jxpfx0ykyb60
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
 * [1]: https://spdx.dev/spdx-specification-21-web-version#h.luq9dgcle9mo
 */
class SpdxLicenseIdExpression(
    val id: String,
    val orLaterVersion: Boolean = false
) : SpdxSimpleExpression() {
    private val spdxLicense by lazy { SpdxLicense.forId(id) }

    override fun decompose() = setOf(this)

    override fun licenses() = listOf(toString())

    override fun simpleLicense() = toString()

    override fun exception(): String? = null

    override fun normalize(mapDeprecated: Boolean) =
        SpdxSimpleLicenseMapping.map(toString(), mapDeprecated) ?: this

    override fun validate(strictness: Strictness) {
        val isValid = SpdxConstants.isNotPresent(id) || when (strictness) {
            Strictness.ALLOW_ANY -> true
            Strictness.ALLOW_DEPRECATED -> spdxLicense != null
            Strictness.ALLOW_CURRENT, Strictness.ALLOW_LICENSEREF_EXCEPTIONS -> spdxLicense?.deprecated == false
        }

        if (!isValid) throw SpdxException("'$this' is not a valid SPDX license id.")
    }

    override fun equals(other: Any?) =
        when (other) {
            is SpdxLicenseIdExpression -> id == other.id && orLaterVersion == other.orLaterVersion

            is SpdxExpression -> {
                val decomposed = other.decompose()
                decomposed.size == 1 && decomposed.first().let {
                    it is SpdxLicenseIdExpression && it.id == id && it.orLaterVersion == orLaterVersion
                }
            }

            else -> false
        }

    override fun hashCode() = id.hashCode() + 31 * orLaterVersion.hashCode()

    override fun toString() =
        buildString {
            append(id)
            // While in the current SPDX standard the "or later version" semantic is part of the id string itself,
            // it is a generic "+" operator for deprecated licenses.
            if (orLaterVersion && !id.endsWith("-or-later")) append("+")
        }

    override fun getLicenseUrl() = if (isValid()) SpdxConstants.LICENSE_LIST_URL + id else null
}

/**
 * An SPDX expression for a license reference [id] as defined by version 2.1 of the
 * [SPDX specification, appendix IV][1].
 *
 * [1]: https://spdx.dev/spdx-specification-21-web-version#h.jxpfx0ykyb60
 */
data class SpdxLicenseReferenceExpression(
    val id: String
) : SpdxSimpleExpression() {
    override fun decompose() = setOf(this)

    override fun licenses() = listOf(id)

    override fun simpleLicense() = id

    override fun exception(): String? = null

    override fun normalize(mapDeprecated: Boolean) = this

    override fun validate(strictness: Strictness) {
        val isLicenseRef = id.startsWith(LICENSE_REF_PREFIX)
        val isDocumentRefToLicenseRef = id.startsWith(DOCUMENT_REF_PREFIX) && ":$LICENSE_REF_PREFIX" in id
        if (!isLicenseRef && !isDocumentRefToLicenseRef) throw SpdxException("'$id' is not an SPDX license reference.")
    }

    override fun equals(other: Any?) =
        when (other) {
            is SpdxLicenseReferenceExpression -> id == other.id

            is SpdxExpression -> {
                val decomposed = other.decompose()
                decomposed.size == 1 && decomposed.first().let { it is SpdxLicenseReferenceExpression && it.id == id }
            }

            else -> false
        }

    override fun hashCode() = id.hashCode()

    override fun toString() = id

    override fun getLicenseUrl(): String? = null
}

/**
 * An SPDX operator for use in compound expressions as defined by version 2.1 of the
 * [SPDX specification, appendix IV][1].
 *
 * [1]: https://spdx.dev/spdx-specification-21-web-version#h.jxpfx0ykyb60
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
