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

package org.ossreviewtoolkit.model.utils

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.common.withoutSuffix
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.Semver
import org.semver4j.processor.IvyProcessor
import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

/** Return true if the version of this [Identifier] is an Ivy version range. */
fun Identifier.hasIvyVersionRange(): Boolean = version.hasIvyVersionRange()

internal fun String.hasIvyVersionRange(): Boolean =
    if (shouldEncode(null, this)) {
        encodeFourIntoThreeSegments()
    } else {
        this
    }.getIvyVersionRanges().get().isNotEmpty()

/**
 * Return true if the version of this [Identifier] interpreted as an Ivy version matcher is applicable to the
 * package with the given [identifier][pkgId].
 */
internal fun Identifier.isApplicableIvyVersion(pkgId: Identifier): Boolean =
    runCatching {
        matchesIvyVersionRange(pkgId.version, version)
    }.onFailure {
        logger.warn {
            "Failed to check if identifier version '$version' is applicable to package version " +
                "'${pkgId.version}' of package '${pkgId.toCoordinates()}'."
        }

        it.showStackTrace()
    }.getOrDefault(false)

/**
 * Return true if [version] is matched by the given [Ivy version matcher][version range]. Throws if the parameters
 * cannot be parsed.
 */
private fun matchesIvyVersionRange(version: String, versionRange: String): Boolean {
    // Support "Exact Revision Matcher" syntax.
    if (versionRange == version) return true

    // Support "Sub Revision Matcher" syntax.
    if (versionRange.withoutSuffix("+")?.let { prefix -> version.startsWith(prefix) } == true) return true

    val (encodedVersion, encodedVersionRange) = encodeVersionAndRange(version, versionRange)

    // `Semver.satisfies(String)` requires a valid version range to work as expected, see:
    // https://github.com/semver4j/semver4j/issues/132.
    val ranges = encodedVersionRange.getIvyVersionRanges()

    return Semver.coerce(encodedVersion)?.satisfies(ranges) == true
}

/**
 * Return an encoded version and version range if [shouldEncode] returns true for the given [version] and
 * [versionRange]. Otherwise, the given [version] and [versionRange] are returned as-is. The encoding works as follows:
 *
 * 1. Enhance all version strings to have four segments, by adding segments with 0 as value to the end if needed.
 * 2. Merge the third and the forth segment into one, by shifting the digits of the third segments 4 to the left.
 *
 * This ensures the encoded version strings have exactly three segments and are supposed to work with Semver4j
 * seamlessly. For example, "1.2.3456.7890" becomes "1.2.3456789", which is a valid semver string.
 */
internal fun encodeVersionAndRange(version: String, versionRange: String): Pair<String, String> =
    if (shouldEncode(version, versionRange)) {
        version.encodeFourIntoThreeSegments() to versionRange.encodeFourIntoThreeSegments()
    } else {
        version to versionRange
    }

// Capture groups:
// 1: Optional 'v' prefix
// 2: Major segment
// 3: Minor segment (optional)
// 4: Patch segment (optional)
// 5: Fourth segment (optional)
// 6: Overflow segments, i.e., 5th+ segments (optional)
private val VERSION_SEGMENTS_REGEX = Regex(
    """(?<![\w.+-])([vV]?)([0-9]+)(?:\.([0-9]+))?(?:\.([0-9]+))?(?:\.([0-9]+))?((?:\.[0-9]+)+)?(?=$|[^\w.])"""
)

/**
 * Returns true if at least one version string with four segments is contained in
 * [version] or [versionRange], but none with more than four segments is contained, and all segments are less than
 * 10000.
 */
private fun shouldEncode(version: String?, versionRange: String): Boolean {
    val matches = listOfNotNull(versionRange, version).flatMap {
        VERSION_SEGMENTS_REGEX.findAll(it).toList()
    }

    val hasUnencodableSegments = matches.any { match ->
        match.groupValues[6].isNotEmpty() || (2..5).any { match.groupValues[it].length > 4 }
    }

    if (hasUnencodableSegments) return false

    return matches.any { it.groupValues[5].isNotEmpty() }
}

/**
 * Replace any 4 segment version string contained in [this] by a 3 segment version string, which has the most minor
 * segments combined.
 */
internal fun String.encodeFourIntoThreeSegments(): String =
    VERSION_SEGMENTS_REGEX.replace(this) { match ->
        fun getSegment(groupIndex: Int): Int = match.groupValues[groupIndex].toIntOrNull() ?: 0

        // Normalize the prefix to lowercase, so that Semver4J's coerce does not drop any pre-release suffix.
        val prefix = match.groupValues[1].lowercase()
        val major = getSegment(2)
        val minor = getSegment(3)
        // The regex matches only segments with maximum four digits, so use 10000 as multiplier.
        val patch = (getSegment(4) * 10000) + getSegment(5)

        "$prefix$major.$minor.$patch"
    }

/**
 * Get the version ranges contained in this string. Return an empty list if no (non-pathological) ranges are contained.
 */
internal fun String.getIvyVersionRanges(): RangeList {
    // Ivy's "latest" status matchers always point to a single version, see
    // https://ant.apache.org/ivy/history/2.6.0/settings/version-matchers.html#_latest_status_matcher.
    if (isBlank() || startsWith("latest")) return RangeList(/* includePreRelease = */ false)

    val ranges = RangeListFactory.create(this, IvyProcessor())

    val isSingleVersion = ranges.get().flatten().singleOrNull { it.toString().startsWith("=") } != null
    if (isSingleVersion) return RangeList(/* includePreRelease = */ false)

    return ranges
}
