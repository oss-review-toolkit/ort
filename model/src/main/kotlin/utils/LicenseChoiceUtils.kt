/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.utils.spdxexpression.SpdxLicenseChoice

/** License views to compute effective licenses for, to use for filtering license choices. */
private val FILTER_LICENSE_VIEWS = setOf(
    LicenseView.ONLY_DECLARED,
    LicenseView.ONLY_DETECTED,
    LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED
)

fun LicenseChoices.filterApplicable(ortResult: OrtResult): LicenseChoices {
    val packageIds = ortResult.getPackages().mapTo(mutableSetOf()) { it.metadata.id }

    val licenseInfoResolver = LicenseInfoResolver(
        provider = DefaultLicenseInfoProvider(ortResult),
        copyrightGarbage = CopyrightGarbage(),
        addAuthorsToCopyrights = false,
        archiver = null,
        licenseFilePatterns = LicenseFilePatterns.getInstance()
    )

    return copy(
        repositoryLicenseChoices = repositoryLicenseChoices.filterRepositoryLicenseChoices(
            packageIds,
            licenseInfoResolver
        ),
        packageLicenseChoices = packageLicenseChoices.filter { it.packageId in packageIds }
    )
}

internal fun List<SpdxLicenseChoice>.filterRepositoryLicenseChoices(
    packageIds: Set<Identifier>,
    licenseInfoResolver: LicenseInfoResolver
): List<SpdxLicenseChoice> {
    val licenseInfos = packageIds.map { id -> licenseInfoResolver.resolveLicenseInfo(id).filterExcluded() }
    val remainingChoices = toMutableSet()

    return buildSet {
        licenseInfos.forEach { licenseInfo ->
            FILTER_LICENSE_VIEWS.forEach { view ->
                val applicableChoices = licenseInfo.effectiveLicenseAndAppliedChoices(
                    view,
                    remainingChoices.toList()
                ).second.toSet()

                addAll(applicableChoices)
                remainingChoices -= applicableChoices

                if (remainingChoices.isEmpty()) return@buildSet
            }
        }
    }.toList()
}
