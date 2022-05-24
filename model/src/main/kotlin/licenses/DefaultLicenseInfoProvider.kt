/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.licenses

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.utils.PackageConfigurationProvider
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense

/**
 * The default [LicenseInfoProvider] that collects license information from an [ortResult].
 */
class DefaultLicenseInfoProvider(
    val ortResult: OrtResult,
    private val packageConfigurationProvider: PackageConfigurationProvider
) : LicenseInfoProvider {
    private val licenseInfo: ConcurrentMap<Identifier, LicenseInfo> = ConcurrentHashMap()

    override fun get(id: Identifier) = licenseInfo.getOrPut(id) { createLicenseInfo(id) }

    private fun createLicenseInfo(id: Identifier): LicenseInfo =
        LicenseInfo(
            id = id,
            concludedLicenseInfo = createConcludedLicenseInfo(id),
            declaredLicenseInfo = createDeclaredLicenseInfo(id),
            detectedLicenseInfo = createDetectedLicenseInfo(id)
        )

    private fun createConcludedLicenseInfo(id: Identifier): ConcludedLicenseInfo =
        ortResult.getPackage(id)?.let { (pkg, curations) ->
            ConcludedLicenseInfo(
                concludedLicense = pkg.concludedLicense,
                appliedCurations = curations.filter { it.curation.concludedLicense != null }
            )
        } ?: ConcludedLicenseInfo(concludedLicense = null, appliedCurations = emptyList())

    private fun createDeclaredLicenseInfo(id: Identifier): DeclaredLicenseInfo =
        ortResult.getProject(id)?.let { project ->
            DeclaredLicenseInfo(
                authors = project.authors,
                licenses = project.declaredLicenses,
                processed = project.declaredLicensesProcessed,
                appliedCurations = emptyList()
            )
        } ?: ortResult.getPackage(id)?.let { (pkg, curations) ->
            DeclaredLicenseInfo(
                authors = pkg.authors,
                licenses = pkg.declaredLicenses,
                processed = pkg.declaredLicensesProcessed,
                appliedCurations = curations.filter { it.curation.declaredLicenseMapping.isNotEmpty() }
            )
        } ?: DeclaredLicenseInfo(
            authors = sortedSetOf(),
            licenses = emptySet(),
            processed = ProcessedDeclaredLicense(null),
            appliedCurations = emptyList()
        )

    private fun createDetectedLicenseInfo(id: Identifier): DetectedLicenseInfo {
        val findings = mutableListOf<Findings>()

        ortResult.getScanResultsForId(id).forEach { (provenance, _, summary) ->
            val (licenseFindingCurations, pathExcludes, relativeFindingsPath) = getConfiguration(id, provenance)

            findings += Findings(
                provenance = provenance,
                licenses = summary.licenseFindings,
                copyrights = summary.copyrightFindings,
                licenseFindingCurations = licenseFindingCurations,
                pathExcludes = pathExcludes,
                relativeFindingsPath = relativeFindingsPath
            )
        }

        return DetectedLicenseInfo(findings)
    }

    private fun getConfiguration(
        id: Identifier,
        provenance: Provenance
    ): Triple<List<LicenseFindingCuration>, List<PathExclude>, String> =
        ortResult.getProject(id)?.let { project ->
            Triple(
                ortResult.repository.config.curations.licenseFindings,
                ortResult.repository.config.excludes.paths,
                ortResult.repository.getRelativePath(project.vcsProcessed).orEmpty()
            )
        } ?: packageConfigurationProvider.getPackageConfigurations(id, provenance).let { packageConfigurations ->
            Triple(
                packageConfigurations.flatMap { it.licenseFindingCurations },
                packageConfigurations.flatMap { it.pathExcludes },
                ""
            )
        }
}
