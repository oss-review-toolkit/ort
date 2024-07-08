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

/**
 * An SPDX operator for composite expressions as defined by version 2.2 of the [SPDX specification, annex D.4][1].
 *
 * [1]: https://spdx.github.io/spdx-spec/v2.2.2/SPDX-license-expressions/#d4-composite-license-expressions
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

/**
 * Create an [SpdxExpression] by concatenating [this][SpdxLicense] and [other] using [SpdxOperator.AND].
 */
infix fun SpdxLicense.and(other: SpdxLicense) = this and other.toExpression()

/**
 * Create an [SpdxExpression] by concatenating [this][SpdxLicense] and [other] using [SpdxOperator.AND].
 */
infix fun SpdxLicense.and(other: SpdxExpression) = SpdxCompoundExpression(toExpression(), SpdxOperator.AND, other)

/**
 * Create an [SpdxExpression] by concatenating [this][SpdxLicense] and [other] using [SpdxOperator.OR].
 */
infix fun SpdxLicense.or(other: SpdxLicense) = this or other.toExpression()

/**
 * Create an [SpdxExpression] by concatenating [this][SpdxLicense] and [other] using [SpdxOperator.OR].
 */
infix fun SpdxLicense.or(other: SpdxExpression) = SpdxCompoundExpression(toExpression(), SpdxOperator.OR, other)

/**
 * Create an [SpdxExpression] by concatenating [this][SpdxLicense] and [exception] using [SpdxExpression.WITH].
 */
infix fun SpdxLicense.with(exception: SpdxLicenseException) =
    SpdxLicenseWithExceptionExpression(toExpression(), exception.id)
