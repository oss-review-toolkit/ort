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
@Suppress("TooManyFunctions")
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
     * Converts this expression into the Disjunctive Normal Form (DNF).
     */
    internal abstract fun toDnf(): Set<Set<String>>

    /**
     * Converts this expression into the Disjunctive Normal Form (DNF) in lowercase.
     */
    private fun toLowerCaseDnf(): Set<Set<String>> =
        toDnf().mapTo(mutableSetOf()) { conjunction ->
            conjunction.mapTo(mutableSetOf()) { singleLicense -> singleLicense.lowercase() }
        }

    final override fun equals(other: Any?): Boolean {
        // Non-functional early returns for performance:
        if (this === other) return true
        if (other !is SpdxExpression) return false
        if (toString().equals(other.toString(), ignoreCase = true)) return true

        return toLowerCaseDnf() == other.toLowerCaseDnf()
    }

    final override fun hashCode(): Int = toLowerCaseDnf().hashCode()

    /**
     * Validate this expression. [strictness] defines whether only the syntax is checked
     * ([ALLOW_ANY][Strictness.ALLOW_ANY]), or semantics are also checked but deprecated license identifiers are
     * allowed ([ALLOW_DEPRECATED][Strictness.ALLOW_DEPRECATED]) or only current license identifiers are allowed
     * ([ALLOW_CURRENT][Strictness.ALLOW_CURRENT]). Throws an [SpdxException] if validation fails.
     */
    abstract fun validate(strictness: Strictness)

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
     * Return true if [choice] is a valid license choice for this SPDX expression.
     */
    fun isValidChoice(choice: SpdxExpression): Boolean = toDnf().dnfIsValidChoice(choice.toDnf())

    /**
     * Return true if [subExpression] is semantically contained in [this][SpdxExpression] expression.
     */
    open fun isSubExpression(subExpression: SpdxExpression?): Boolean = this == subExpression

    /**
     * Return true if this expression offers a license choice. This can only be true if this expression contains the
     * [OR operator][SpdxOperator.OR].
     */
    fun offersChoice(): Boolean = toDnf().size > 1

    /**
     * Return true if this expression satisfies the [other] expression.
     */
    fun satisfies(other: SpdxExpression): Boolean {
        val conjunctions = toDnf().ifEmpty { return false }
        val otherConjunctions = other.toDnf().ifEmpty { return false }

        return conjunctions.any { conjunction ->
            otherConjunctions.any { otherConjunction ->
                otherConjunction.all { requiredLicense ->
                    conjunction.any { offeredLicense ->
                        offeredLicense.singleLicenseSatisfies(requiredLicense)
                    }
                }
            }
        }
    }

    /**
     * Returns this expression with all sub-expressions that match [subExpression] replaced by [replacement] based on
     * semantic equivalence.
     */
    internal abstract fun replaceSubExpression(
        subExpression: SpdxExpression,
        replacement: SpdxExpression
    ): SpdxExpression

    /**
     * Apply a license [choice], and return an [SpdxExpression] where the choice is resolved.
     * The returned expression is reconstructed from a filtered DNF, so the returned expression tree is flattened and
     * the original tree structure and parentheses get discarded.
     */
    fun applyChoice(choice: SpdxExpression): SpdxExpression {
        val dnf = toDnf()
        val otherDnf = choice.toDnf()

        if (!dnf.dnfIsValidChoice(otherDnf)) {
            throw InvalidLicenseChoiceException("Cannot select $choice for expression $this.")
        }

        return choice
    }

    /**
     * Apply [choice] individually to any expression tree node which is semantically equivalent to [subExpression],
     * replacing such nodes new ones where the choice is resolved. The expression tree is processed recursively to
     * replace only the matched subtrees, while preserving the remaining original tree structure.
     */
    fun applyChoice(choice: SpdxExpression, subExpression: SpdxExpression): SpdxExpression {
        if (!choice.satisfies(subExpression)) {
            throw InvalidLicenseChoiceException("Cannot select $choice for subexpression $subExpression.")
        }

        return replaceSubExpression(subExpression, choice).also {
            if (it === this) {
                throw InvalidSubExpressionException(
                    "The subExpression $subExpression does not match any subtree in $this."
                )
            }
        }
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
        if (!offersChoice()) return this to emptyList()

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

/**
 * Return [this] DNF converted to an [SpdxExpression]. The result is simplified dynamically
 * based on semantic 'or-later' relationship sets to avoid structural redundancies like "A or A+" and
 * also A or (A AND B).
 */
internal fun Set<Set<String>>.dnfToSpdxExpression(): SpdxExpression {
    if (isEmpty()) return SpdxExpression.NONE

    // If a conjunction contains '-or-later' and '-only' of the same license, discard the former.
    val cleanedConjunctions = map { conjunction ->
        conjunction.filter { specificLicense ->
            conjunction.none { otherLicense ->
                specificLicense != otherLicense && otherLicense.singleLicenseSatisfies(specificLicense)
            }
        }.toSet()
    }.toSet()

    // Simplify redundancies of the form "A" OR "A+", to just "A+".
    val minimizedDnf = cleanedConjunctions.filter { currentConjunction ->
        cleanedConjunctions.none { otherConjunction ->
            currentConjunction != otherConjunction && otherConjunction.all { otherLicense ->
                currentConjunction.any { currentLicense ->
                    currentLicense.singleLicenseSatisfies(otherLicense)
                }
            }
        }
    }

    val conjunctionExpressions = minimizedDnf.map { conjunction ->
        when (conjunction.size) {
            0 -> throw IllegalArgumentException("A DNF conjunction must not be empty.")

            1 -> SpdxExpression.parse(conjunction.single())

            else -> SpdxCompoundExpression(
                operator = SpdxOperator.AND,
                children = conjunction.map { singleLicense ->
                    SpdxExpression.parse(singleLicense)
                }.sortedBy { it.toString().lowercase() }
            )
        }
    }.sortedBy { it.toString().lowercase() }

    return conjunctionExpressions.singleOrNull()
        ?: SpdxCompoundExpression(SpdxOperator.OR, conjunctionExpressions)
}

/**
 * Return true if the choice corresponding to [choiceDnf] is a valid choice for the expression corresponding to [this]
 * expression DNF. A valid choice is allowed to contain OR operators, but is not allowed to over-satisfy, for example
 * given "A" choose "A AND B".
 */
private fun Set<Set<String>>.dnfIsValidChoice(choiceDnf: Set<Set<String>>): Boolean =
    choiceDnf.ifEmpty { return false }.all { choiceConjunction ->
        any { conjunction ->
            conjunction.size == choiceConjunction.size && choiceConjunction.all { choiceLicense ->
                conjunction.any { license ->
                    choiceLicense.singleLicenseSatisfies(license)
                }
            }
        }
    }

/**
 * Filter this DNF and return only the conjunctions (paths) that are semantically satisfied by the [other] DNF.
 */
private fun Set<Set<String>>.dnfFilterSatisfiedBy(other: Set<Set<String>>): Set<Set<String>> =
    filterTo(mutableSetOf()) { conjunction ->
        other.any { otherConjunction ->
            otherConjunction.all { otherSingleLicense ->
                conjunction.any { singleLicense ->
                    singleLicense.singleLicenseSatisfies(otherSingleLicense)
                }
            }
        }
    }

/**
 * Return true if [this] single license satisfies [otherSingleLicense] license string.
 */
private fun String.singleLicenseSatisfies(otherSingleLicense: String): Boolean =
    SpdxOrLaterLicenseMapper.INSTANCE.satisfies(offered = this, required = otherSingleLicense)
