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

package org.ossreviewtoolkit.model.licenses

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.utils.FindingsMatcher
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression

class LicenseInfoResolver(val provider: LicenseInfoProvider) {
    private val resolvedLicenseInfo: ConcurrentMap<Identifier, ResolvedLicenseInfo> = ConcurrentHashMap()

    /**
     * Get the [ResolvedLicenseInfo] for the project or package identified by [id].
     * TODO: Add options to filter output, e.g. "filter excluded findings".
     */
    fun resolveLicenseInfo(id: Identifier) = resolvedLicenseInfo.getOrPut(id) { createLicenseInfo(id) }

    private fun createLicenseInfo(id: Identifier): ResolvedLicenseInfo {
        val licenseInfo = provider.get(id)

        val concludedLicenses = licenseInfo.concludedLicenseInfo.concludedLicense?.decompose().orEmpty()
        val declaredLicenses = licenseInfo.declaredLicenseInfo.processed.spdxExpression?.decompose().orEmpty()
        val detectedLicenses = licenseInfo.detectedLicenseInfo.findings.flatMapTo(mutableSetOf()) { findings ->
            findings.licenses.flatMap { it.license.decompose() }
        }

        val resolvedLicenses = mutableMapOf<SpdxSingleLicenseExpression, ResolvedLicenseBuilder>()

        fun SpdxSingleLicenseExpression.builder() =
            resolvedLicenses.getOrPut(this) { ResolvedLicenseBuilder(this) }

        // Handle concluded licenses.
        concludedLicenses.forEach { license ->
            license.builder().sources += LicenseSource.CONCLUDED
        }

        // Handle declared licenses.
        declaredLicenses.forEach { license ->
            license.builder().apply {
                sources += LicenseSource.DECLARED

                originalDeclaredLicenses.addAll(
                    licenseInfo.declaredLicenseInfo.processed.mapped.filterValues { it == license }.keys
                )
            }
        }

        // Handle detected licenses.
        val unmatchedCopyrights = mutableMapOf<Provenance, MutableSet<CopyrightFinding>>()
        val resolvedLocations = resolveLocations(licenseInfo.detectedLicenseInfo, unmatchedCopyrights)

        detectedLicenses.forEach { license ->
            license.builder().apply {
                sources += LicenseSource.DETECTED
                resolvedLocations[license]?.let { locations.addAll(it) }
            }
        }

        return ResolvedLicenseInfo(id, resolvedLicenses.values.map { it.build() }, unmatchedCopyrights)
    }

    private fun resolveLocations(
        detectedLicenseInfo: DetectedLicenseInfo,
        unmatchedCopyrights: MutableMap<Provenance, MutableSet<CopyrightFinding>>
    ): Map<SpdxSingleLicenseExpression, Set<ResolvedLicenseLocation>> {
        val resolvedLocations = mutableMapOf<SpdxSingleLicenseExpression, MutableSet<ResolvedLicenseLocation>>()

        detectedLicenseInfo.findings.forEach { findings ->
            // TODO: Apply license finding curations.
            // TODO: Apply path excludes.
            val matchResult = FindingsMatcher().matchFindings(findings.licenses, findings.copyrights)
            matchResult.matchedFindings.forEach { (licenseFinding, copyrightFindings) ->
                val resolvedCopyrightFindings = copyrightFindings.mapTo(mutableSetOf()) { copyrightFinding->
                    // TODO: Filter copyright garbage.
                    // TODO: Process copyright statements (and keep original statements).
                    ResolvedCopyrightFinding(
                        statement = copyrightFinding.statement,
                        originalStatements = emptySet(),
                        locations = setOf(copyrightFinding.location)
                    )
                }

                licenseFinding.license.decompose().forEach { singleLicense ->
                    resolvedLocations.getOrPut(singleLicense) { mutableSetOf() } += ResolvedLicenseLocation(
                        findings.provenance,
                        licenseFinding.location.path,
                        licenseFinding.location.startLine,
                        licenseFinding.location.endLine,
                        appliedCuration = null,
                        matchingPathExcludes = emptyList(),
                        copyrights = resolvedCopyrightFindings
                    )
                }
            }

            unmatchedCopyrights.getOrPut(findings.provenance) { mutableSetOf() } += matchResult.unmatchedCopyrights
        }

        return resolvedLocations
    }
}

private class ResolvedLicenseBuilder(val license: SpdxSingleLicenseExpression) {
    val sources = mutableSetOf<LicenseSource>()
    var originalDeclaredLicenses = mutableSetOf<String>()
    var locations = mutableSetOf<ResolvedLicenseLocation>()

    fun build() = ResolvedLicense(license, sources, originalDeclaredLicenses, locations)
}
