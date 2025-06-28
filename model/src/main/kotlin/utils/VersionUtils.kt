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
package org.ossreviewtoolkit.model.utils

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.Semver
import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

/**
 * A list of Strings that are used by Ivy-style version ranges.
 */
private val IVY_VERSION_RANGE_INDICATORS = listOf(",", "~", "*", "+", ">", "<", "=", " - ", "^", ".x", "||")

/**
 * Return true if the version of this [Identifier] interpreted as an Ivy version matcher is applicable to the
 * package with the given [identifier][pkgId].
 */
internal fun Identifier.isApplicableIvyVersion(pkgId: Identifier) =
    runCatching {
        if (version == pkgId.version) return true

        // `Semver.satisfies(String)` requires a valid version range to work as expected, see:
        // https://github.com/semver4j/semver4j/issues/132.
        val ranges = getVersionRanges() ?: return false

        return Semver.coerce(pkgId.version)?.satisfies(ranges) == true
    }.onFailure {
        logger.warn {
            "Failed to check if identifier version '$version' is applicable to package version " +
                "'${pkgId.version}' of package '${pkgId.toCoordinates()}'."
        }

        it.showStackTrace()
    }.getOrDefault(false)

internal fun Identifier.isVersionRange(): Boolean {
    val ranges = getVersionRanges()?.get()?.flatten() ?: return false
    val rangeVersions = ranges.mapTo(mutableSetOf()) { it.rangeVersion }
    val isSingleVersion = rangeVersions.size <= 1 && ranges.all { range ->
        // Determine whether the non-accessible `Range.rangeOperator` is `RangeOperator.EQUALS`.
        range.toString().startsWith("=")
    }

    return !isSingleVersion
}

private fun Identifier.getVersionRanges(): RangeList? {
    if (IVY_VERSION_RANGE_INDICATORS.none { version.contains(it, ignoreCase = true) }) return null

    return runCatching {
        RangeListFactory.create(version).takeUnless { it.get().isEmpty() }
    }.getOrNull()
}
