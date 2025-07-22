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

package org.ossreviewtoolkit.utils.ort

import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.common.withoutSuffix

private val versionSeparators = listOf('-', '_', '.')
private val versionSeparatorsPattern = versionSeparators.joinToString("", "[", "]")

private val ignorablePrefixSuffix = listOf("rel", "release", "final")
private val ignorablePrefixSuffixPattern = ignorablePrefixSuffix.joinToString("|", "(", ")")
private val ignorablePrefixSuffixRegex = Regex(
    "(^$ignorablePrefixSuffixPattern$versionSeparatorsPattern|$versionSeparatorsPattern$ignorablePrefixSuffixPattern$)"
)

/**
 * Filter a list of [names] to include only those that likely belong to the given [version] of an optional [project].
 */
fun filterVersionNames(version: String, names: List<String>, project: String? = null): List<String> {
    if (version.isBlank() || names.isEmpty()) return emptyList()

    // If there are full matches, return them right away.
    val fullMatches = names.filter { it.equals(version, ignoreCase = true) }
    if (fullMatches.isNotEmpty()) return fullMatches

    // Create variants of the version string to recognize.
    data class VersionVariant(val name: String, val separators: List<Char>)

    val versionLower = version.lowercase()
    val versionVariants = mutableListOf(VersionVariant(versionLower, versionSeparators))

    val separatorRegex = Regex(versionSeparatorsPattern)
    versionSeparators.mapTo(versionVariants) {
        VersionVariant(versionLower.replace(separatorRegex, it.toString()), listOf(it))
    }

    ignorablePrefixSuffix.mapTo(versionVariants) {
        VersionVariant(versionLower.removeSuffix(it).trimEnd(*versionSeparators.toCharArray()), versionSeparators)
    }

    // The list of supported version separators.
    val versionHasSeparator = versionSeparators.any { it in version }

    val filteredNames = names.filter {
        val name = it.lowercase().replace(ignorablePrefixSuffixRegex, "")

        versionVariants.any { versionVariant ->
            // Allow to ignore suffixes in names that are separated by something else than the current separator, e.g.
            // for version "3.3.1" accept "3.3.1-npm-packages" but not "3.3.1.0".
            val hasIgnorableSuffixOnly = name.withoutPrefix(versionVariant.name)?.let { tail ->
                tail.firstOrNull() !in versionVariant.separators
            } == true

            // Allow to ignore prefixes in names that are separated by something else than the current separator, e.g.
            // for version "0.10" accept "docutils-0.10" but not "1.0.10".
            val hasIgnorablePrefixOnly = name.withoutSuffix(versionVariant.name)?.let { head ->
                val last = head.lastOrNull()
                val forelast = head.dropLast(1).lastOrNull()

                val currentSeparators = if (versionHasSeparator) versionVariant.separators else versionSeparators

                // Full match with the current version variant.
                last == null
                    // The prefix does not end with the current separators or a digit.
                    || (last !in currentSeparators && !last.isDigit())
                    // The prefix ends with the current separators but the forelast character is not a digit.
                    || (last in currentSeparators && (forelast == null || !forelast.isDigit()))
                    // The prefix ends with 'v' and the forelast character is a separator.
                    || (last == 'v' && (forelast == null || forelast in currentSeparators))
            } == true

            hasIgnorableSuffixOnly || hasIgnorablePrefixOnly
        }
    }

    return filteredNames.filter {
        // startsWith("") returns "true" for any string, so this yields an unfiltered list if "project" is "null".
        it.startsWith(project.orEmpty())
    }.let {
        // Fall back to the original list if filtering by project results in an empty list.
        it.ifEmpty { filteredNames }
    }
}
