/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.spdxexpression

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer

import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdxexpression.parser.SpdxExpressionParser

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
         * A constant for [NONE][SpdxConstants.NONE] as an expression.
         */
        val NONE = SpdxLicenseIdExpression(SpdxConstants.NONE)

        /**
         * A constant for [NOASSERTION][SpdxConstants.NOASSERTION] as an expression.
         */
        val NOASSERTION = SpdxLicenseIdExpression(SpdxConstants.NOASSERTION)

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
     * Normalize all license IDs using [SpdxSimpleLicenseMapper]. The result of this function is not guaranteed to
     * contain only valid IDs. Use [validate] or [isValid] to check the returned [SpdxExpression] for validity
     * afterwards.
     */
    abstract fun normalize(): SpdxExpression

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
    fun applyChoices(licenseChoices: List<SpdxLicenseChoice>): SpdxExpression = applyAndGetChoices(licenseChoices).first

    /**
     * Apply [licenseChoices] in the given order to [this][SpdxExpression] and return the resulting SPDX expression
     * along with the applied license choices in the order they were applied.
     */
    fun applyAndGetChoices(licenseChoices: List<SpdxLicenseChoice>): Pair<SpdxExpression, List<SpdxLicenseChoice>> {
        if (validChoices().size == 1) return this to emptyList()

        var currentExpression = this
        val appliedChoices = mutableListOf<SpdxLicenseChoice>()

        licenseChoices.forEach {
            if (it.given == null && currentExpression.isValidChoice(it.choice)) {
                currentExpression = currentExpression.applyChoice(it.choice)
                appliedChoices += it
            } else if (currentExpression.isSubExpression(it.given)) {
                @Suppress("UnsafeCallOnNullableType")
                currentExpression = currentExpression.applyChoice(it.choice, it.given!!)
                appliedChoices += it
            }
        }

        return currentExpression to appliedChoices
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
