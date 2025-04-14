/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.utils.spdx.SpdxConstants.DOCUMENT_REF_PREFIX
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.LICENSE_REF_PREFIX
import org.ossreviewtoolkit.utils.spdx.parser.SpdxExpressionParser

/**
 * An SPDX expression as defined by version 2.2 of the [SPDX specification, annex D][1].
 *
 * [1]: https://spdx.github.io/spdx-spec/v2.2.2/SPDX-license-expressions/
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
        fun parse(expression: String, strictness: Strictness): SpdxExpression =
            SpdxExpressionParser(expression, strictness).parse()
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
    fun licenses(): List<String> =
        decompose().map {
            val simpleExpression = if (it is SpdxLicenseWithExceptionExpression) it.license else it
            simpleExpression.toString()
        }

    /**
     * Normalize all license IDs using [SpdxSimpleLicenseMapping]. If [mapDeprecated] is `true`, this involves mapping
     * deprecated IDs to their current counterparts. If [mapSimple] is `true`, also commonly known abbreviations or
     * aliases are mapped. The result of this function is not guaranteed to contain only valid IDs. Use [validate] or
     * [isValid] to check the returned [SpdxExpression] for validity afterwards.
     */
    abstract fun normalize(mapDeprecated: Boolean = true, mapSimple: Boolean = true): SpdxExpression

    /**
     * Return a simplified expression that has e.g. redundancies removed.
     */
    open fun simplify(): SpdxExpression = this

    /**
     * Return this expression sorted lexicographically.
     */
    open fun sorted(): SpdxExpression = this

    /**
     * Validate this expression. [strictness] defines whether only the syntax is checked
     * ([ALLOW_ANY][Strictness.ALLOW_ANY]), or semantics are also checked but deprecated license identifiers are
     * allowed ([ALLOW_DEPRECATED][Strictness.ALLOW_DEPRECATED]) or only current license identifiers are allowed
     * ([ALLOW_CURRENT][Strictness.ALLOW_CURRENT]). Throws an [SpdxException] if validation fails.
     */
    abstract fun validate(strictness: Strictness)

    /**
     * Return all valid license choices for this SPDX expression by collecting all disjunct expressions.
     */
    open fun validChoices(): Set<SpdxExpression> = setOf(this)

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
     * Return true if [subExpression] is semantically contained in [this][SpdxExpression] expression.
     */
    open fun isSubExpression(subExpression: SpdxExpression?): Boolean = this == subExpression

    /**
     * Return true if this expression offers a license choice. This can only be true if this expression contains the
     * [OR operator][SpdxOperator.OR].
     */
    fun offersChoice(): Boolean = validChoices().size > 1

    /**
     * Apply a license [choice], optionally limited to the given [subExpression], and return an [SpdxExpression] where
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
                @Suppress("UnsafeCallOnNullableType")
                currentExpression = currentExpression.applyChoice(it.choice, it.given!!)
            }
        }

        return currentExpression
    }

    /**
     * Concatenate [this][SpdxExpression] and [other] using [SpdxOperator.AND].
     */
    infix fun and(other: SpdxExpression) =
        takeIf { this == other } ?: SpdxCompoundExpression(SpdxOperator.AND, listOf(this, other))

    /**
     * Concatenate [this][SpdxExpression] and [other] using [SpdxOperator.OR].
     */
    infix fun or(other: SpdxExpression) =
        takeIf { this == other } ?: SpdxCompoundExpression(SpdxOperator.OR, listOf(this, other))
}

/**
 * An SPDX composite expression using an [operator] with a list of [child][children] expressions as defined by version
 * 2.2 of the [SPDX specification, annex D.4][1].
 *
 * [1]: https://spdx.github.io/spdx-spec/v2.2.2/SPDX-license-expressions/#d4-composite-license-expressions
 */
class SpdxCompoundExpression(
    val operator: SpdxOperator,
    val children: Collection<SpdxExpression>
) : SpdxExpression() {
    /**
     * Create a compound expression with the provided [operator] and the [left] and [right] child expressions.
     */
    constructor(left: SpdxExpression, operator: SpdxOperator, right: SpdxExpression) :
        this(operator, listOf(left, right))

    /**
     * Create a compound expression with the provided [operator], the [first] and [second] child expressions, and an
     * arbitrary number of [other] child expressions.
     */
    constructor(operator: SpdxOperator, first: SpdxExpression, second: SpdxExpression, vararg other: SpdxExpression) :
        this(operator, listOf(first, second, *other))

    init {
        require(children.size > 1) {
            "A compound expression must have at least two children, but has only ${children.size}."
        }
    }

    override fun decompose(): Set<SpdxSingleLicenseExpression> = children.flatMapTo(mutableSetOf()) { it.decompose() }

    override fun normalize(mapDeprecated: Boolean, mapSimple: Boolean) =
        SpdxCompoundExpression(operator, children.map { it.normalize(mapDeprecated, mapSimple) })

    private fun flatten(): SpdxExpression {
        val flattenedChildren = children.flatMapTo(mutableSetOf()) { child ->
            val simplifiedChild = child.simplify()

            if (simplifiedChild is SpdxCompoundExpression && simplifiedChild.operator == operator) {
                // Inline nested children of the same operator.
                simplifiedChild.children.map { if (it is SpdxCompoundExpression) it.flatten() else it }
            } else {
                setOf(simplifiedChild)
            }
        }

        return flattenedChildren.singleOrNull() ?: SpdxCompoundExpression(operator, flattenedChildren)
    }

    override fun simplify(): SpdxExpression =
        // Eliminate redundant choices by creating the set of unique choices and using the choice if there is only one.
        validChoices().singleOrNull()?.let {
            if (it is SpdxCompoundExpression) it.flatten() else it
        } ?: flatten()

    override fun sorted(): SpdxExpression {
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
                    children += child.sorted()
                }
            }

            expression.children.forEach { addChildren(it) }

            return children.sortedBy { it.toString() }
        }

        return checkNotNull(getSortedChildrenWithSameOperator(this).toExpression(operator))
    }

    override fun validate(strictness: Strictness) {
        children.forEach { it.validate(strictness) }
    }

    override fun validChoices(): Set<SpdxExpression> =
        when (operator) {
            SpdxOperator.AND -> {
                // If there is a top-level `AND` in an expression, create the combinations of all choices on the left
                // and all choices on the right to get the overall valid choices.
                children.fold(setOf()) { acc, child ->
                    if (acc.isEmpty()) {
                        child.validChoices()
                    } else {
                        child.validChoices().flatMapTo(mutableSetOf()) { childChoice ->
                            acc.map { operand ->
                                // Do not use the "and" operator here which performs an optimization for equal operands
                                // that could lead to endless recursion. However, perform a similar optimization that at
                                // least handles equal string representations.
                                operand.takeIf { it.toString() == childChoice.toString() }
                                    ?: SpdxCompoundExpression(SpdxOperator.AND, listOf(operand, childChoice))
                            }
                        }
                    }
                }
            }

            SpdxOperator.OR -> {
                // If there is a top-level `OR` in an expression, the operands already are the choices and just need to
                // be checked themselves for choices.
                children.flatMapTo(mutableSetOf()) { it.validChoices() }
            }
        }

    override fun applyChoice(choice: SpdxExpression, subExpression: SpdxExpression): SpdxExpression {
        if (!subExpression.isSubExpression(choice)) {
            throw InvalidLicenseChoiceException(
                "$choice is not a valid choice for $subExpression. Valid choices are: ${subExpression.validChoices()}."
            )
        }

        if (!isSubExpression(subExpression)) {
            throw InvalidSubExpressionException("$subExpression is not a valid subExpression of $this")
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

            requireNotNull(unchosenLicenses.toExpression(SpdxOperator.OR)) {
                "No licenses left after applying choice $choice to $subExpression."
            }
        }
    }

    override fun isSubExpression(subExpression: SpdxExpression?): Boolean =
        when {
            subExpression == null -> false
            operator == SpdxOperator.AND && children.any { it.isSubExpression(subExpression) } -> true
            else -> validChoices().containsAll(subExpression.validChoices())
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
        // respective expression in parentheses. Semantically, this would only be required if the priority of this
        // operator is higher than the priority of the operator of the left or right expression, but always adding
        // parentheses makes it easier to understand the expression.
        buildString {
            children.forEachIndexed { index, child ->
                if (index > 0) append(" $operator ")

                when {
                    child is SpdxCompoundExpression && operator != child.operator -> append("($child)")
                    else -> append("$child")
                }
            }
        }
}

/**
 * An SPDX expression that contains only a single license with an optional exception. Can be either a
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
     * Return the license exception identifier if this is an [SpdxLicenseWithExceptionExpression] or null otherwise.
     */
    abstract fun exception(): String?

    /**
     * Return the URL for the licence if this is an [SpdxLicenseIdExpression] or [SpdxLicenseWithExceptionExpression].
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
        private val EXCEPTION_STRING_PATTERN = Regex(
            "\\b(exception|additional-terms|no-patent)\\b",
            RegexOption.IGNORE_CASE
        )

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

    override fun simpleLicense() = license.toString()

    override fun exception() = exception

    override fun normalize(mapDeprecated: Boolean, mapSimple: Boolean) =
        // Manually cast to SpdxSingleLicenseExpression as the type resolver does not recognize that in all subclasses
        // of SpdxSimpleExpression normalize() returns an SpdxSingleLicenseExpression.
        when (val normalizedLicense = license.normalize(mapDeprecated, mapSimple) as SpdxSingleLicenseExpression) {
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
                decomposed.singleOrNull()?.let {
                    it is SpdxLicenseWithExceptionExpression && it.license == license && it.exception == exception
                } == true
            }

            else -> false
        }

    override fun hashCode() = license.hashCode() + 31 * exception.hashCode()

    override fun toString(): String = "$license $WITH $exception"

    override fun getLicenseUrl() = license.getLicenseUrl()
}

/**
 * An SPDX simple expression as defined by version 2.2 of the [SPDX specification, annex D.3][1]. A simple expression
 * can be either an [SpdxLicenseIdExpression] or an [SpdxLicenseReferenceExpression].
 *
 * [1]: https://spdx.github.io/spdx-spec/v2.2.2/SPDX-license-expressions/#d3-simple-license-expressions
 */
sealed class SpdxSimpleExpression : SpdxSingleLicenseExpression() {
    /**
     * Concatenate [this][SpdxSimpleExpression] and [other] using [SpdxExpression.WITH].
     */
    infix fun with(other: String) = SpdxLicenseWithExceptionExpression(this, other)
}

/**
 * An SPDX expression for a license [id] as defined by version 2.2 of the [SPDX specification, annex D][1].
 * [orLaterVersion] indicates whether the license id also describes later versions of the license.
 *
 * [1]: https://spdx.github.io/spdx-spec/v2.2.2/SPDX-license-expressions/
 */
class SpdxLicenseIdExpression(
    val id: String,
    val orLaterVersion: Boolean = false
) : SpdxSimpleExpression() {
    private val spdxLicense by lazy { SpdxLicense.forId(id) }

    override fun decompose() = setOf(this)

    override fun simpleLicense() = toString()

    override fun exception(): String? = null

    override fun normalize(mapDeprecated: Boolean, mapSimple: Boolean) =
        SpdxSimpleLicenseMapping.map(toString(), mapDeprecated, mapSimple) ?: this

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
                decomposed.singleOrNull()?.let {
                    it is SpdxLicenseIdExpression && it.id == id && it.orLaterVersion == orLaterVersion
                } == true
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
 * An SPDX expression for a license reference [id] as defined by version 2.2 of the [SPDX specification, annex D][1].
 *
 * [1]: https://spdx.github.io/spdx-spec/v2.2.2/SPDX-license-expressions/
 */
data class SpdxLicenseReferenceExpression(
    val id: String
) : SpdxSimpleExpression() {
    override fun decompose() = setOf(this)

    override fun simpleLicense() = id

    override fun exception(): String? = null

    override fun normalize(mapDeprecated: Boolean, mapSimple: Boolean) = this

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
                decomposed.singleOrNull()?.let { it is SpdxLicenseReferenceExpression && it.id == id } == true
            }

            else -> false
        }

    override fun hashCode() = id.hashCode()

    override fun toString() = id

    override fun getLicenseUrl(): String? = null
}
