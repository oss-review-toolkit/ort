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

import java.lang.invoke.MethodHandles

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdxexpression.SpdxExpression.Strictness

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
 * Return true if this string can be successfully parsed to a [SpdxExpression] of the given [strictness].
 */
fun String.isSpdxExpression(strictness: Strictness = Strictness.ALLOW_DEPRECATED): Boolean =
    runCatching { SpdxExpression.parse(this, strictness) }.isSuccess

/**
 * Return true if this String can be successfully parsed to an [SpdxExpression] with the given [strictness],
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
 * Parse a YAML string which contains a top-level sequence of key-value pairs.
 */
internal fun String.parseYamlKeyValueLines(): Map<String, String> =
    lineSequence()
        .map { it.trim() }
        .filterNot { it.isEmpty() || it.startsWith('#') || it == "---" || ':' !in it }
        .associate {
            // Values must not contain ":", so if there are more than one, they must be part of the key.
            it.substringBeforeLast(':').trim().removeSurrounding("\"") to
                it.substringAfterLast(':').trim()
        }
