/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import com.fasterxml.jackson.annotation.JsonInclude
import com.vdurmont.semver4j.Semver

import java.util.EnumSet

/**
 * Details about the used source code scanner.
 */
data class ScannerDetails(
    /**
     * The name of the scanner.
     */
    val name: String,

    /**
     * The version of the scanner.
     */
    val version: String,

    /**
     * The configuration of the scanner, could be command line arguments for example.
     */
    val configuration: String,

    /**
     * An object with a representation of the scanner's command line options. While the [configuration] property
     * holds the exact configuration of the scanner, this field supports advanced compatibility checks of
     * configuration options.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val options: ScannerOptions? = null
) {
    companion object {
        /**
         * A constant for a [ScannerDetails] where all properties are empty strings.
         */
        @JvmField
        val EMPTY = ScannerDetails(
            name = "",
            version = "",
            configuration = ""
        )

        private val MAJOR_MINOR = EnumSet.of(Semver.VersionDiff.MAJOR, Semver.VersionDiff.MINOR)
    }

    /**
     * True if the [other] scanner has the same name and configuration, and the [Semver] version differs only in other
     * parts than [major][Semver.VersionDiff.MAJOR] and [minor][Semver.VersionDiff.MINOR]. For the comparison the
     * [loose][Semver.SemverType.LOOSE] Semver type is used for maximum compatibility with the versions returned from
     * the scanners.
     */
    fun isCompatible(other: ScannerDetails) =
        name.equals(other.name, ignoreCase = true) && configuration == other.configuration &&
                isCompatibleVersion(other.version)

    /**
     * Check whether the specified [otherVersion] is compatible with the version of this scanner details. For the
     * comparison the [loose][Semver.SemverType.LOOSE] Semver type is used for maximum compatibility.
     */
    fun isCompatibleVersion(otherVersion: String) =
        Semver(version, Semver.SemverType.LOOSE).diff(otherVersion) !in MAJOR_MINOR
}
