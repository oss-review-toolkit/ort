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

/**
 * Return true if the version of this [Identifier] is an Ivy version range.
 */
fun Identifier.hasIvyVersionRange(): Boolean = version.getIvyVersionRanges().get().isNotEmpty()

/**
 * Return true if the version of this [Identifier] interpreted as an Ivy version matcher is applicable to the
 * package with the given [identifier][pkgId].
 */
internal fun Identifier.isApplicableIvyVersion(pkgId: Identifier): Boolean =
    runCatching {
        // Support "Exact Revision Matcher" syntax.
        if (version == pkgId.version) return true

        // Support "Sub Revision Matcher" syntax.
        if (version.withoutSuffix("+")?.let { prefix -> pkgId.version.startsWith(prefix) } == true) return true

        // `Semver.satisfies(String)` requires a valid version range to work as expected, see:
        // https://github.com/semver4j/semver4j/issues/132.
        val ranges = version.getIvyVersionRanges()

        return Semver.coerce(pkgId.version)?.satisfies(ranges) == true
    }.onFailure {
        logger.warn {
            "Failed to check if identifier version '$version' is applicable to package version " +
                "'${pkgId.version}' of package '${pkgId.toCoordinates()}'."
        }

        it.showStackTrace()
    }.getOrDefault(false)

/**
 * Get the version ranges contained in this string. Return an empty list if no (non-pathological) ranges are contained.
 */
internal fun String.getIvyVersionRanges(): RangeList {
    if (isBlank()) return RangeList(/* includePreRelease = */ false)

    val ranges = RangeListFactory.create(this, IvyProcessor())

    val isSingleVersion = ranges.get().flatten().singleOrNull { it.toString().startsWith("=") } != null
    if (isSingleVersion) return RangeList(/* includePreRelease = */ false)

    return ranges
}
