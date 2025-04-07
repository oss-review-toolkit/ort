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

import java.lang.invoke.MethodHandles

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.spdx.SpdxExpression.Strictness

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/**
 * Convert an [SpdxExpression] to `NOASSERTION` if null, to `NONE` if blank, or to its string representation otherwise.
 */
fun SpdxExpression?.nullOrBlankToSpdxNoassertionOrNone(): String =
    when {
        this == null -> SpdxConstants.NOASSERTION
        toString().isBlank() -> SpdxConstants.NONE
        else -> toString()
    }

/**
 * Combine this collection of [SpdxExpression]s into one using the [operator], or return null if the collection is
 * empty.
 */
fun Collection<SpdxExpression>.toExpression(operator: SpdxOperator = SpdxOperator.AND): SpdxExpression? =
    distinct().run {
        when (size) {
            0 -> null
            1 -> first()
            else -> SpdxCompoundExpression(operator, this)
        }
    }

/**
 * Create an [SpdxLicenseIdExpression] from this [SpdxLicense].
 */
fun SpdxLicense.toExpression(): SpdxLicenseIdExpression {
    var expressionId = id

    // While in the current SPDX standard the "or later version" semantic is part of the id string itself, it is a
    // generic "+" operator for deprecated licenses.
    val orLaterVersion = if (deprecated) {
        expressionId = id.removeSuffix("+")
        id != expressionId
    } else {
        id.endsWith("-or-later")
    }

    return SpdxLicenseIdExpression(expressionId, orLaterVersion)
}

/**
 * Return true if and only if this string can be successfully parsed to a [SpdxExpression] of the given [strictness].
 */
fun String.isSpdxExpression(strictness: Strictness = Strictness.ALLOW_DEPRECATED): Boolean =
    runCatching { SpdxExpression.parse(this, strictness) }.isSuccess

/**
 * Return true if and only if this String can be successfully parsed to an [SpdxExpression] with the given [strictness],
 * or if it equals [SpdxConstants.NONE] or [SpdxConstants.NOASSERTION].
 */
fun String.isSpdxExpressionOrNotPresent(strictness: Strictness = Strictness.ALLOW_DEPRECATED): Boolean =
    SpdxConstants.isNotPresent(this) || isSpdxExpression(strictness)

/**
 * Parse this string as an [SpdxExpression] of the given [strictness] and return the result on success, or throw an
 * [SpdxException] if the string cannot be parsed.
 */
fun String.toSpdx(strictness: Strictness = Strictness.ALLOW_ANY): SpdxExpression =
    SpdxExpression.parse(this, strictness)

/**
 * Parse this string as an [SpdxExpression] of the given [strictness] and return the result on success, or null if this
 * string cannot be parsed.
 */
fun String.toSpdxOrNull(strictness: Strictness = Strictness.ALLOW_ANY): SpdxExpression? =
    runCatching {
        toSpdx(strictness)
    }.onFailure {
        logger.debug { "Could not parse '$this' as an SPDX license: ${it.collectMessages()}" }
    }.getOrNull()

/**
 * Convert a [String] to an SPDX "idstring" (like license IDs, package IDs, etc.) which may only contain letters,
 * numbers, ".", and / or "-". If [allowPlusSuffix] is enabled, a "+" (as used in license IDs) is kept as the suffix.
 */
fun String.toSpdxId(allowPlusSuffix: Boolean = false): String {
    val hasPlusSuffix = endsWith('+')
    val specialValid = setOf('-', '.')
    val specialInvalid = setOf(':', '_')

    val converted = buildString {
        var lastChar: Char? = null

        this@toSpdxId.forEach { c ->
            when {
                // Take allowed chars as-is.
                c.isLetterOrDigit() || c in specialValid -> c

                // Replace colons and underscores with dashes. Do not allow consecutive special chars for readability.
                c in specialInvalid -> '-'.takeUnless { lastChar in specialValid }

                // Replace anything else with dots. Do not allow consecutive special chars for readability.
                else -> '.'.takeUnless { lastChar in specialValid }
            }?.let {
                append(it)
                lastChar = it
            }
        }
    }

    // Do not allow leading or trailing special chars for readability.
    val trimmed = converted.trim { it in specialValid }

    return trimmed.takeUnless { hasPlusSuffix && allowPlusSuffix } ?: "$trimmed+"
}
