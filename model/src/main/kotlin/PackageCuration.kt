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

import org.ossreviewtoolkit.model.utils.isApplicableIvyVersion

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
     * Return true if this [PackageCuration] is applicable to the package with the given [identifier][pkgId]. The
     * curation's version may be an
     * [Ivy version matcher](http://ant.apache.org/ivy/history/2.4.0/settings/version-matchers.html).
     */
    fun isApplicable(pkgId: Identifier): Boolean =
        isApplicableDisregardingVersion(pkgId)
            && (id.version.equalsOrIsBlank(pkgId.version) || id.isApplicableIvyVersion(pkgId))

    /**
     * Apply the curation [data] to the provided [basePackage] by calling [PackageCurationData.apply], if applicable.
     */
    fun apply(basePackage: CuratedPackage): CuratedPackage {
        require(isApplicable(basePackage.metadata.id)) {
            "Package curation identifier '${id.toCoordinates()}' does not match package identifier " +
                "'${basePackage.metadata.id.toCoordinates()}'."
        }

        return data.apply(basePackage)
    }
}
