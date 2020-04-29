/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFindings
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.spdx.SpdxExpression

import java.util.SortedSet

internal class LicenseResolver(
    private val ortResult: OrtResult,
    private val packageConfigurationProvider: PackageConfigurationProvider
) {
    private val curationMatcher = FindingCurationMatcher()
    private val findingsMatcher = FindingsMatcher()

    fun collectLicenseFindings(
        omitExcluded: Boolean = false
    ): Map<Identifier, Map<LicenseFindings, List<PathExclude>>> =
        ortResult.getProjectAndPackageIds()
            .filter { id -> !omitExcluded || !ortResult.isPackageExcluded(id) }
            .associateWith { id -> collectLicenseFindings(id).toMutableMap() }

    fun getDetectedLicensesForId(id: Identifier): SortedSet<String> =
        collectLicenseFindings(id).keys.mapTo(sortedSetOf()) { it.license }

    private fun getPathExcludes(id: Identifier, provenance: Provenance): List<PathExclude> =
        if (ortResult.isProject(id)) {
            ortResult.getExcludes().paths
        } else {
            packageConfigurationProvider.getPackageConfiguration(id, provenance)?.pathExcludes.orEmpty()
        }

    private fun getLicenseFindingCurations(id: Identifier, provenance: Provenance): List<LicenseFindingCuration> =
        if (ortResult.isProject(id)) {
            ortResult.getLicenseFindingsCurations(id)
        } else {
            packageConfigurationProvider.getPackageConfiguration(id, provenance)?.licenseFindingCurations.orEmpty()
        }

    private fun TextLocation.getRelativePathToRoot(id: Identifier): String =
        ortResult.getProject(id)?.let { ortResult.getFilePathRelativeToAnalyzerRoot(it, path) } ?: path

    private fun collectLicenseFindings(
        id: Identifier
    ): Map<LicenseFindings, List<PathExclude>> {
        val result = mutableMapOf<LicenseFindings, List<PathExclude>>()

        ortResult.getScanResultsForId(id).forEach { scanResult ->
            val curations = getLicenseFindingCurations(id, scanResult.provenance)
            val pathExcludes = getPathExcludes(id, scanResult.provenance)

            val rawLicenseFindings = scanResult.summary.licenseFindings
            val copyrightFindings = scanResult.summary.copyrightFindings.filter { copyright ->
                pathExcludes.none { it.matches(copyright.location.getRelativePathToRoot(id)) }
            }

            val curatedLicenseFindings = curationMatcher.applyAll(rawLicenseFindings, curations)
            val decomposedFindings = curatedLicenseFindings.flatMap { finding ->
                SpdxExpression.parse(finding.license).decompose().map { finding.copy(license = it.toString()) }
            }
            val matchedFindings = findingsMatcher.match(decomposedFindings, copyrightFindings).toSortedSet()

            matchedFindings.forEach { findings ->
                val matchingExcludes = mutableSetOf<PathExclude>()

                val isExcluded = findings.locations.all { location ->
                    val path = location.getRelativePathToRoot(id)
                    pathExcludes.any { exclude ->
                        exclude.matches(path).also { matches ->
                            if (matches) matchingExcludes += exclude
                        }
                    }
                }

                // Only add matching excludes if all license locations are excluded.
                result[findings] = if (isExcluded) matchingExcludes.toList() else emptyList()
            }
        }

        return result
    }

    fun getDetectedLicensesWithCopyrights(id: Identifier, omitExcluded: Boolean = true): Map<String, Set<String>> =
        collectLicenseFindings(id)
            .filter { (_, excludes) -> !omitExcluded || excludes.isEmpty() }
            .map { (findings, _) -> findings }
            .associateBy(
                { it.license },
                { it.copyrights.mapTo(mutableSetOf()) { it.statement } }
            )
}
