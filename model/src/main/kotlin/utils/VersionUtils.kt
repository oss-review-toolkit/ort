/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import kotlin.collections.isNotEmpty

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.RangesListFactory
import org.semver4j.Semver

/**
 * A list of Strings that are used to identify a version string as a version range in the [PackageCuration]'s version.
 */
private val versionRangeIndicators = listOf(",", "~", "*", "+", ">", "<", "=", " - ", "^", ".x", "||")

/**
 * Return true if the version of this [PackageCuration] interpreted as an Ivy version matcher is applicable to the
 * package with the given [identifier][pkgId].
 */
internal fun Identifier.isApplicableIvyVersion(pkgId: Identifier) =
    runCatching {
        if (version == pkgId.version) return true

        if (version.isVersionRange()) {
            // `Semver.satisfies(String)` requires a valid version range to work as expected, see:
            // https://github.com/semver4j/semver4j/issues/132.
            val range = RangesListFactory.create(version)
            require(range.get().isNotEmpty()) {
                "'$version' is not a valid version range."
            }

            return Semver.coerce(pkgId.version)?.satisfies(range) == true
        }

        return false
    }.onFailure {
        logger.warn {
            "Failed to check if package curation version '$version' is applicable to package version " +
                "'${pkgId.version}' of package '${pkgId.toCoordinates()}'."
        }

        it.showStackTrace()
    }.getOrDefault(false)

private fun String.isVersionRange() = versionRangeIndicators.any { contains(it, ignoreCase = true) }
