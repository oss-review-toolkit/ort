/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.config

data class LicenseFilenamePatterns(
    /**
     * A list of globs that match default license file names. The patterns are supposed to be used case-insensitively.
     */
    val licenseFilenames: List<String>,

    /**
     * A list of globs that match default patent file names. The patterns are supposed to be used case-insensitively.
     */
    val patentFilenames: List<String>,

    /**
     * A list of globs that match files that often define the root license of a project, but are no license files and
     * are therefore not contained in [licenseFilenames]. The patterns are supposed to be used case-insensitively.
     */
    val rootLicenseFilenames: List<String>
) {
    /**
     * A list of globs that match all kind of license file names, equaling the union of [licenseFilenames],
     * [patentFilenames] and [rootLicenseFilenames]. The patterns are supposed to be used case-insensitively.
     */
    val allLicenseFilenames = (licenseFilenames + patentFilenames + rootLicenseFilenames).distinct()

    companion object {
        val DEFAULT = LicenseFilenamePatterns(
            licenseFilenames = listOf(
                "copying*",
                "copyright",
                "licence*",
                "license*",
                "*.licence",
                "*.license",
                "unlicence",
                "unlicense"
            ),
            patentFilenames = listOf(
                "patents"
            ),
            rootLicenseFilenames = listOf(
                "readme*"
            )
        )

        private var instance: LicenseFilenamePatterns = DEFAULT

        @Synchronized
        fun configure(patterns: LicenseFilenamePatterns) {
            instance = patterns
        }

        @Synchronized
        fun getInstance(): LicenseFilenamePatterns = instance
    }
}
