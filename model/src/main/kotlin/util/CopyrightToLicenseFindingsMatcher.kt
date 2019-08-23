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

package com.here.ort.model.util

import com.here.ort.model.CopyrightFinding
import com.here.ort.model.CopyrightFindings
import com.here.ort.model.LicenseFinding
import com.here.ort.model.LicenseFindings
import com.here.ort.spdx.LicenseFileMatcher

import java.util.SortedMap
import java.util.SortedSet

import kotlin.math.absoluteValue

/**
 * A class for matching copyright findings to license findings.
 */
class CopyrightToLicenseFindingsMatcher(
    private val licenseFileMatcher: LicenseFileMatcher = LicenseFileMatcher.DEFAULT_MATCHER
) {
    /**
     * Get the license found in one of the commonly named license files, if any, or an empty string otherwise.
     */
    internal fun getRootLicense(licenseFindings: List<LicenseFinding>): String =
        // TODO: This function should return a list of all licenses found in all license files instead of only a single
        // license.
        licenseFindings.singleOrNull { finding ->
            licenseFileMatcher.matches(finding.location.path)
        }?.license ?: ""

    /**
     * Return the copyright statements in the vicinity, as specified by [toleranceLines], of [licenseStartLine] in the
     * file [path]. The default value of [toleranceLines] is set to 5 which seems to be a good balance between
     * associating findings separated by blank lines but not skipping complete license statements.
     */
    internal fun getClosestCopyrightStatements(
        copyrights: List<CopyrightFinding>,
        licenseStartLine: Int,
        toleranceLines: Int = 5
    ): SortedSet<CopyrightFindings> {
        require(
            copyrights.map { it.location.path }.distinct().size <= 1,
            { "Given copyright statements must all point to the same file." }
        )

        val closestCopyrights = copyrights.filter {
            (it.location.startLine - licenseStartLine).absoluteValue <= toleranceLines
        }

        return closestCopyrights.map { it.toCopyrightFindings() }.toSortedSet()
    }

    private fun CopyrightFinding.toCopyrightFindings() =
        CopyrightFindings(
            statement = statement,
            locations = sortedSetOf(location)
        )

    /**
     * Associate copyright findings to license findings within a single file.
     */
    private fun associateFileFindings(
        licenses: List<LicenseFinding>,
        copyrights: List<CopyrightFinding>,
        rootLicense: String = ""
    ): SortedMap<String, MutableSet<CopyrightFindings>> {
        require(
            (licenses.map { it.location.path } + copyrights.map { it.location.path }).distinct().size <= 1,
            { "The given license and copyright findings must all point to the same file." }
        )

        val copyrightsForLicenses = sortedMapOf<String, MutableSet<CopyrightFindings>>()
        val allCopyrightStatements = copyrights.map { it.toCopyrightFindings() }.toSortedSet()

        when (licenses.size) {
            0 -> {
                // If there is no license finding but copyright findings, associate them with the root license, if any.
                if (allCopyrightStatements.isNotEmpty() && rootLicense.isNotEmpty()) {
                    copyrightsForLicenses[rootLicense] = allCopyrightStatements
                }
            }

            1 -> {
                // If there is only a single license finding, associate all copyright findings with that license.
                val licenseId = licenses.single().license
                copyrightsForLicenses[licenseId] = allCopyrightStatements
            }

            else -> {
                // If there are multiple license findings in a single file, search for the closest copyright statements
                // for each of these, if any.
                licenses.forEach {
                    val closestCopyrights = getClosestCopyrightStatements(
                        copyrights = copyrights,
                        licenseStartLine = it.location.startLine
                    )
                    copyrightsForLicenses.getOrPut(it.license) { sortedSetOf() } += closestCopyrights
                }
            }
        }

        return copyrightsForLicenses
    }

    fun associateFindings(licenseFindings: List<LicenseFinding>, copyrightFindings: List<CopyrightFinding>):
            SortedSet<LicenseFindings> {
        val licenseFindingsByPath = licenseFindings.groupBy { it.location.path }
        val copyrightFindingsByPath = copyrightFindings.groupBy { it.location.path }
        val paths = (licenseFindingsByPath.keys + copyrightFindingsByPath.keys).toSet()
        val rootLicense = getRootLicense(licenseFindings)

        val locationsForLicenses = licenseFindings
            .groupBy({ it.license }, { it.location })
            .mapValues { it.value.toSortedSet() }
            .toSortedMap()

        val copyrightsForLicenses = sortedMapOf<String, SortedSet<CopyrightFindings>>()
        paths.forEach { path ->
            val licenses = licenseFindingsByPath[path].orEmpty()
            val copyrights = copyrightFindingsByPath[path].orEmpty()
            val findings = associateFileFindings(licenses, copyrights, rootLicense)

            findings.forEach { (license, copyrightsForLicense) ->
                copyrightsForLicenses.getOrPut(license) { sortedSetOf() }.let { copyrightFindings ->
                    copyrightsForLicense.forEach { copyrightFinding ->
                        copyrightFindings.find { it.statement == copyrightFinding.statement }?.let {
                            it.locations += copyrightFinding.locations
                        } ?: copyrightFindings.add(copyrightFinding)
                    }
                }
            }
        }

        return (copyrightsForLicenses.keys + locationsForLicenses.keys).map { license ->
            LicenseFindings(
                license,
                locationsForLicenses[license] ?: sortedSetOf(),
                copyrightsForLicenses[license] ?: sortedSetOf()
            )
        }.toSortedSet()
    }
}
