/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver

import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.showStackTrace

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
            // TODO: This check does not completely comply to the Ivy version-matchers specification. E.g. the version
            //       '1.0' does not satisfy the version range [1.0,2.0], see
            //       https://github.com/vdurmont/semver4j/issues/67.
            Requirement.buildIvy(id.version).isSatisfiedBy(Semver(pkgId.version, Semver.SemverType.LOOSE))
        }.onFailure {
            log.warn {
                "Failed to check if package curation version '${id.version}' is applicable to package version " +
                        "'${pkgId.version}' of package '${pkgId.toCoordinates()}'."
            }

            it.showStackTrace()
        }.getOrDefault(false)

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
        require(isApplicable(targetPackage.pkg.id)) {
            "Package curation identifier '${id.toCoordinates()}' does not match package identifier " +
                    "'${targetPackage.pkg.id.toCoordinates()}'."
        }

        return data.apply(targetPackage)
    }
}
