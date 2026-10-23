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

    override fun normalize() = SpdxCompoundExpression(operator, children.map { it.normalize() })

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

            return children.sortedBy { it.toString().lowercase() }
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
        // If the operator is AND, then there is no choice at this level, but try with the children if they contain the
        // sub-expression. Also, if the sub-expression is the whole expression, no recursive application of the choice
        // is needed, as it will be handled by the logic below.
        if (
            operator == SpdxOperator.AND && subExpression != this && children.any { it.isSubExpression(subExpression) }
        ) {
            // Reconstruct this AND-expression with all children having the subexpression replaced with the choice.
            return SpdxCompoundExpression(
                SpdxOperator.AND,
                children.map {
                    when {
                        it is SpdxCompoundExpression -> it.replaceSubexpressionWithChoice(subExpression, choice)
                        else -> it
                    }
                }
            )
        }

        // The basic idea of the following algorithm is that any expression can be represented as a disjunction of the
        // choices it offers, and that any expressions that offer exactly the same choices are equal. So an expression
        // is first "decomposed" into its choices, then dismissed choices (unchosen expressions) are removed, and the
        // remaining expressions are recomped with the OR-operand.

        val choices = validChoices().toMutableSet()
        val subExpressionChoices = subExpression.validChoices()
        val dismissedChoices = subExpressionChoices - choice.validChoices()

        // While the sub-expression is still contained in this expression...
        while (choices.containsAll(subExpressionChoices)) {
            // ... remove all unchosen expressions.
            choices.removeAll(dismissedChoices)
        }

        require(choices.isNotEmpty()) {
            "No licenses left after applying choice $choice to $subExpression."
        }

        return choices.reduce(SpdxExpression::or)
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
