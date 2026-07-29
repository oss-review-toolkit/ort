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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.licenses.ConcludedLicenseInfo
import org.ossreviewtoolkit.model.licenses.DeclaredLicenseInfo
import org.ossreviewtoolkit.model.licenses.DetectedLicenseInfo
import org.ossreviewtoolkit.model.licenses.Findings
import org.ossreviewtoolkit.model.licenses.LicenseInfo
import org.ossreviewtoolkit.model.licenses.LicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.SimpleLicenseInfoProvider
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdxexpression.SpdxExpression
import org.ossreviewtoolkit.utils.spdxexpression.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdxexpression.toSpdx

class LicenseChoiceUtilsTest : WordSpec({
    "filterRepositoryLicenseChoices()" should {
        "keep choices which apply in license views: declared, detected, concluded" {
            val provider = SimpleLicenseInfoProviderBuilder().set(
                identifier = "Maven:a:b:1.0.0",
                declared = "Apache-2.0 OR BSD-3-Clause",
                detected = "MIT OR BSD-2-Clause",
                concluded = "Apache-1.1 OR Apache-1.0"
            ).build()

            val choices = listOf(
                SpdxLicenseChoice(
                    given = "Apache-2.0 OR BSD-3-Clause".toSpdx(),
                    choice = "BSD-3-Clause".toSpdx()
                ),
                SpdxLicenseChoice(
                    given = "Apache-1.1 OR Apache-1.0".toSpdx(),
                    choice = "Apache-1.1".toSpdx()
                ),
                SpdxLicenseChoice( // not applicable.
                    given = "Apache-1.1 OR BSD-3-Clause".toSpdx(),
                    choice = "BSD-3-Clause".toSpdx()
                )
            )

            choices.filterRepositoryLicenseChoices(
                provider.getIdentifiers(),
                provider.createResolver()
            ).map { it.given.toString() to it.choice.toString() } should containExactlyInAnyOrder(
                "Apache-2.0 OR BSD-3-Clause" to "BSD-3-Clause",
                "Apache-1.1 OR Apache-1.0" to "Apache-1.1"
            )
        }

        "keep choices which apply in license view 'declared and detected' if concluded license is absent" {
            val provider = SimpleLicenseInfoProviderBuilder().set(
                identifier = "Maven:a:b:1.0.0",
                declared = "Apache-2.0 OR BSD-3-Clause",
                detected = "MIT OR BSD-2-Clause",
                concluded = null
            ).build()

            val choices = listOf(
                SpdxLicenseChoice(
                    given = "(Apache-2.0 OR BSD-3-Clause) AND (MIT OR BSD-2-Clause)".toSpdx(),
                    choice = "Apache-2.0 AND MIT".toSpdx()
                ),
                SpdxLicenseChoice(
                    given = "(Apache-2.0 OR BSD-3-Clause) AND MIT".toSpdx(),
                    choice = "Apache-2.0 AND MIT".toSpdx()
                )
            )

            choices.filterRepositoryLicenseChoices(
                provider.getIdentifiers(),
                provider.createResolver()
            ).map { it.given.toString() to it.choice.toString() } should containExactlyInAnyOrder(
                "(Apache-2.0 OR BSD-3-Clause) AND (MIT OR BSD-2-Clause)" to "Apache-2.0 AND MIT"
            )
        }

        "keep a choice matching only a subexpression" {
            val provider = SimpleLicenseInfoProviderBuilder().set(
                identifier = "Maven:a:b:1.0.0",
                declared = "(Apache-2.0 OR BSD-3-Clause) AND MIT"
            ).build()

            val choices = listOf(
                SpdxLicenseChoice(
                    given = "Apache-2.0 OR BSD-3-Clause".toSpdx(),
                    choice = "Apache-2.0".toSpdx()
                )
            )

            choices.filterRepositoryLicenseChoices(
                provider.getIdentifiers(),
                provider.createResolver()
            ).map { it.given.toString() to it.choice.toString() } should containExactlyInAnyOrder(
                "Apache-2.0 OR BSD-3-Clause" to "Apache-2.0"
            )
        }
    }
})

private class SimpleLicenseInfoProviderBuilder {
    val licensesForId = mutableMapOf<Identifier, Triple<SpdxExpression?, SpdxExpression?, SpdxExpression?>>()

    fun set(identifier: String, declared: String? = null, detected: String? = null, concluded: String? = null) =
        apply {
            licensesForId[Identifier(identifier)] = Triple(declared?.toSpdx(), detected?.toSpdx(), concluded?.toSpdx())
        }

    fun build(): SimpleLicenseInfoProvider {
        val licenseInfos = licensesForId.map { (id, licenses) ->
            val (declared, detected, concluded) = licenses

            LicenseInfo(
                id = id,
                declaredLicenseInfo = DeclaredLicenseInfo(
                    authors = emptySet(),
                    licenses = setOfNotNull(declared?.toString()),
                    processed = ProcessedDeclaredLicense(
                        spdxExpression = declared
                    ),
                    appliedCurations = emptyList()
                ),
                detectedLicenseInfo = DetectedLicenseInfo(
                    findings = listOf(
                        Findings(
                            provenance = UnknownProvenance,
                            licenses = setOfNotNull(
                                detected?.let {
                                    LicenseFinding(
                                        license = it,
                                        location = TextLocation(
                                            path = "LICENSE",
                                            startLine = 1,
                                            endLine = 2
                                        )
                                    )
                                }
                            ),
                            copyrights = emptySet(),
                            licenseFindingCurations = emptyList(),
                            pathExcludes = emptyList(),
                            relativeFindingsPath = ""
                        )
                    )
                ),
                concludedLicenseInfo = ConcludedLicenseInfo(
                    concludedLicense = concluded,
                    appliedCurations = emptyList()
                )
            )
        }

        return SimpleLicenseInfoProvider(licenseInfos)
    }
}

private fun LicenseInfoProvider.createResolver() =
    LicenseInfoResolver(
        provider = this,
        copyrightGarbage = CopyrightGarbage(),
        addAuthorsToCopyrights = false,
        archiver = null
    )
