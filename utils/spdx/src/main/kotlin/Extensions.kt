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

package org.ossreviewtoolkit.utils.spdx

/**
 * Convert a null or blank [String] to `NONE`.
 */
fun String?.nullOrBlankToSpdxNone(): String = if (isNullOrBlank()) SpdxConstants.NONE else this

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
