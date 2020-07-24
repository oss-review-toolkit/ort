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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.utils.ProcessedDeclaredLicense

/**
 * The default [LicenseInfoProvider] that collects license information from an [ortResult].
 */
class DefaultLicenseInfoProvider(val ortResult: OrtResult) : LicenseInfoProvider {
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
        ortResult.getPackage(id)?.let { curatedPkg ->
            ConcludedLicenseInfo(
                concludedLicense = curatedPkg.pkg.concludedLicense,
                appliedCurations = curatedPkg.curations.filter { it.curation.concludedLicense != null }
            )
        } ?: ConcludedLicenseInfo(concludedLicense = null, appliedCurations = emptyList())

    private fun createDeclaredLicenseInfo(id: Identifier): DeclaredLicenseInfo =
        ortResult.getProject(id)?.let { project ->
            DeclaredLicenseInfo(
                licenses = project.declaredLicenses,
                processed = project.declaredLicensesProcessed,
                appliedCurations = emptyList()
            )
        } ?: ortResult.getPackage(id)?.let { curatedPkg ->
            DeclaredLicenseInfo(
                licenses = curatedPkg.pkg.declaredLicenses,
                processed = curatedPkg.pkg.declaredLicensesProcessed,
                appliedCurations = curatedPkg.curations.filter { it.curation.declaredLicenses != null }
            )
        } ?: DeclaredLicenseInfo(
            licenses = emptySet(),
            processed = ProcessedDeclaredLicense(null),
            appliedCurations = emptyList()
        )

    private fun createDetectedLicenseInfo(id: Identifier): DetectedLicenseInfo {
        val findings = mutableListOf<Findings>()

        ortResult.getScanResultsForId(id).forEach { scanResult ->
            findings += Findings(
                provenance = scanResult.provenance,
                licenses = scanResult.summary.licenseFindings,
                copyrights = scanResult.summary.copyrightFindings
            )
        }

        return DetectedLicenseInfo(findings)
    }
}
