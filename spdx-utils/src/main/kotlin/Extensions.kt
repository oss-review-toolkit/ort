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

import java.util.EnumSet

/**
 * Return an [EnumSet] that contains the elements of [this] and [other].
 */
operator fun <E : Enum<E>> EnumSet<E>.plus(other: EnumSet<E>): EnumSet<E> = EnumSet.copyOf(this).apply { addAll(other) }

/**
 * Create an [SpdxExpression] by concatenating [this][SpdxLicense] and [other] using [SpdxOperator.AND].
 */
infix fun SpdxLicense.and(other: SpdxLicense) = this and other.toExpression()

/**
 * Create an [SpdxExpression] by concatenating [this][SpdxLicense] and [other] using [SpdxOperator.AND].
 */
infix fun SpdxLicense.and(other: SpdxExpression) =
        SpdxCompoundExpression(toExpression(), SpdxOperator.AND, other)

/**
 * Create an [SpdxExpression] by concatenating [this][SpdxLicense] and [other] using [SpdxOperator.OR].
 */
infix fun SpdxLicense.or(other: SpdxLicense) = this or other.toExpression()

/**
 * Create an [SpdxExpression] by concatenating [this][SpdxLicense] and [other] using [SpdxOperator.OR].
 */
infix fun SpdxLicense.or(other: SpdxExpression) =
        SpdxCompoundExpression(toExpression(), SpdxOperator.OR, other)

/**
 * Create an [SpdxExpression] by concatenating [this][SpdxLicense] and [exception] using [SpdxOperator.WITH].
 */
infix fun SpdxLicense.with(exception: SpdxLicenseException) =
        SpdxCompoundExpression(toExpression(), SpdxOperator.WITH, exception.toExpression())

/**
 * Create an [SpdxLicenseIdExpression] from this [SpdxLicense].
 */
fun SpdxLicense.toExpression() = SpdxLicenseIdExpression(id)

/**
 * Create an [SpdxLicenseExceptionExpression] from this [SpdxLicenseException].
 */
fun SpdxLicenseException.toExpression() = SpdxLicenseExceptionExpression(id)

/**
 * Return whether this [String] is a LicenseRef to [name], by default [ignoring case][ignoreCase]. Any possible
 * (scanner-specific) namespaces are ignored.
 */
fun String.isLicenseRefTo(name: String, ignoreCase: Boolean = true): Boolean {
    if (name.isBlank()) return false

    val withoutPrefix = removePrefix("LicenseRef-")
    if (withoutPrefix == this) return false

    if (!withoutPrefix.endsWith(name, ignoreCase)) return false
    val infix = withoutPrefix.dropLast(name.length)

    return infix.indexOf('-') == infix.length - 1
}
