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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonProperty

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.RangesListFactory
import org.semver4j.Semver

/**
 * A list of Strings that are used to identify a version string as a version range in the [PackageCuration]'s version.
 */
private val versionRangeIndicators = listOf(",", "~", "*", "+", ">", "<", "=", " - ", "^", ".x", "||")

/**
 * Return true if this string equals the [other] string, or if either string is blank.
 */
private fun String.equalsOrIsBlank(other: String) = equals(other) || isBlank() || other.isBlank()

/**
 * This class assigns a [PackageCurationData] object to a [Package] identified by the [id].
 */
data class PackageCuration(
    /**
     * The identifier of the package.
     */
    val id: Identifier,

    /**
     * The curation data for the package.
     */
    @JsonProperty("curations")
    val data: PackageCurationData
) {
    companion object : Logging

    /**
     * Return true if this [PackageCuration] is applicable to the package with the given [identifier][pkgId],
     * disregarding the version.
     */
    private fun isApplicableDisregardingVersion(pkgId: Identifier) =
        id.type.equals(pkgId.type, ignoreCase = true)
                && id.namespace == pkgId.namespace
                && id.name.equalsOrIsBlank(pkgId.name)

    /**
     * Return true if the version of this [PackageCuration] interpreted as an Ivy version matcher is applicable to the
     * package with the given [identifier][pkgId].
     */
    private fun isApplicableIvyVersion(pkgId: Identifier) =
        runCatching {
            if (id.version == pkgId.version) return true

            if (id.version.isVersionRange()) {
                // `Semver.satisfies(String)` requires a valid version range to work as expected, see:
                // https://github.com/semver4j/semver4j/issues/132.
                val range = RangesListFactory.create(id.version)
                require(range.get().size > 0) {
                    "'${id.version}' is not a valid version range."
                }

                return Semver.coerce(pkgId.version).satisfies(range)
            }

            return false
        }.onFailure {
            logger.warn {
                "Failed to check if package curation version '${id.version}' is applicable to package version " +
                        "'${pkgId.version}' of package '${pkgId.toCoordinates()}'."
            }

            it.showStackTrace()
        }.getOrDefault(false)

    private fun String.isVersionRange() = versionRangeIndicators.any { contains(it, ignoreCase = true) }

    /**
     * Return true if this [PackageCuration] is applicable to the package with the given [identifier][pkgId]. The
     * curation's version may be an
     * [Ivy version matcher](http://ant.apache.org/ivy/history/2.4.0/settings/version-matchers.html).
     */
    fun isApplicable(pkgId: Identifier): Boolean =
        isApplicableDisregardingVersion(pkgId)
                && (id.version.equalsOrIsBlank(pkgId.version) || isApplicableIvyVersion(pkgId))

    /**
     * Apply the curation [data] to the provided [targetPackage].
     *
     * @see [PackageCurationData.apply]
     */
    fun apply(targetPackage: CuratedPackage): CuratedPackage {
        require(isApplicable(targetPackage.metadata.id)) {
            "Package curation identifier '${id.toCoordinates()}' does not match package identifier " +
                    "'${targetPackage.metadata.id.toCoordinates()}'."
        }

        return data.apply(targetPackage)
    }
}
