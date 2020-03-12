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

package com.here.ort.model.utils

import com.here.ort.model.Identifier
import com.here.ort.model.LicenseFindings
import com.here.ort.model.OrtResult
import com.here.ort.model.TextLocation
import com.here.ort.model.config.PathExclude

import java.util.SortedSet

internal class LicenseResolver(private val ortResult: OrtResult) {
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

    private fun getPathExcludes(id: Identifier): List<PathExclude> =
        // Only license findings of projects can be excluded by path excludes.
        // TODO: Implement path excludes for packages.
        if (ortResult.isProject(id)) ortResult.getExcludes().paths else emptyList()

    private fun TextLocation.getRelativePathToRoot(id: Identifier): String =
        ortResult.getProject(id)?.let { ortResult.getFilePathRelativeToAnalyzerRoot(it, path) } ?: path

    private fun collectLicenseFindings(
        id: Identifier
    ): Map<LicenseFindings, List<PathExclude>> {
        val result = mutableMapOf<LicenseFindings, List<PathExclude>>()
        val pathExcludes = getPathExcludes(id)

        val matchedFindings = ortResult.getScanResultsForId(id).flatMap { scanResult ->
            val rawLicenseFindings = scanResult.summary.licenseFindings
            val copyrightFindings = scanResult.summary.copyrightFindings.filter { copyright ->
                pathExcludes.none { it.matches(copyright.location.getRelativePathToRoot(id)) }
            }
            val curations = ortResult.getLicenseFindingsCurations(id)

            val curatedLicenseFindings = curationMatcher.applyAll(rawLicenseFindings, curations)
            findingsMatcher.match(curatedLicenseFindings, copyrightFindings).toSortedSet()
        }

        matchedFindings.forEach { findings ->
            val matchingExcludes = mutableSetOf<PathExclude>()

            // Only license findings of projects can be excluded by path excludes.
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
