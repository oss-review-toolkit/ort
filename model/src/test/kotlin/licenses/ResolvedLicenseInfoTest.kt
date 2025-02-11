/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.mockk

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

class ResolvedLicenseInfoTest : WordSpec() {
    init {
        "effectiveLicense()" should {
            "apply choices for LicenseView.ALL on all resolved licenses" {
                // All: (Apache-2.0 WITH LLVM-exception OR MIT) AND (MIT OR GPL-2.0-only) AND (0BSD OR GPL-2.0-only)
                val choices = listOf(
                    SpdxLicenseChoice("$APACHE OR $MIT".toSpdx(), MIT.toSpdx()),
                    SpdxLicenseChoice("$MIT OR $GPL".toSpdx(), MIT.toSpdx()),
                    SpdxLicenseChoice("$BSD OR $GPL".toSpdx(), BSD.toSpdx())
                )

                val effectiveLicense = RESOLVED_LICENSE_INFO.effectiveLicense(LicenseView.ALL, choices)

                effectiveLicense shouldBe "$MIT AND $BSD".toSpdx()
            }

            "apply a choice for a sub-expression only" {
                // Declared: Apache-2.0 WITH LLVM-exception OR MIT OR GPL-2.0-only
                val resolvedLicenseInfo = ResolvedLicenseInfo(
                    id = Identifier.EMPTY,
                    licenseInfo = mockk(),
                    licenses = listOf(
                        ResolvedLicense(
                            license = APACHE.toSpdx() as SpdxSingleLicenseExpression,
                            originalDeclaredLicenses = setOf("$APACHE OR $MIT"),
                            originalExpressions = setOf(
                                ResolvedOriginalExpression("$APACHE OR $MIT OR $GPL".toSpdx(), LicenseSource.DECLARED)
                            ),
                            locations = emptySet()
                        )
                    ),
                    copyrightGarbage = emptyMap(),
                    unmatchedCopyrights = emptyMap()
                )

                val choices = listOf(
                    SpdxLicenseChoice("$APACHE OR $MIT".toSpdx(), MIT.toSpdx())
                )

                val effectiveLicense = resolvedLicenseInfo.effectiveLicense(LicenseView.ONLY_DECLARED, choices)

                effectiveLicense shouldBe "$MIT OR $GPL".toSpdx()
            }

            "apply choices for LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED" {
                // Concluded: 0BSD OR GPL-2.0-only
                val choices = listOf(
                    SpdxLicenseChoice("$BSD OR $GPL".toSpdx(), BSD.toSpdx())
                )

                val effectiveLicense = RESOLVED_LICENSE_INFO.effectiveLicense(
                    LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED,
                    choices
                )

                effectiveLicense shouldBe BSD.toSpdx()
            }

            "apply choices for LicenseView.ONLY_DECLARED" {
                // Declared: Apache-2.0 WITH LLVM-exception OR MIT
                val choices = listOf(
                    SpdxLicenseChoice("$APACHE OR $MIT".toSpdx(), MIT.toSpdx())
                )

                val effectiveLicense = RESOLVED_LICENSE_INFO.effectiveLicense(
                    LicenseView.ONLY_DECLARED,
                    choices
                )

                effectiveLicense shouldBe MIT.toSpdx()
            }

            "apply package and repository license choice for LicenseView.ONLY_CONCLUDED in the correct order" {
                val repositoryChoices = listOf(
                    SpdxLicenseChoice("$APACHE OR $MIT".toSpdx(), MIT.toSpdx()),
                    SpdxLicenseChoice("$BSD OR $GPL".toSpdx(), BSD.toSpdx())
                )
                val packageChoices = listOf(
                    SpdxLicenseChoice("$APACHE OR $MIT".toSpdx(), APACHE.toSpdx())
                )

                val effectiveLicense = RESOLVED_LICENSE_INFO.effectiveLicense(
                    LicenseView.ALL,
                    packageChoices,
                    repositoryChoices
                )

                effectiveLicense shouldBe "$APACHE and ($MIT or $GPL) and $BSD".toSpdx()
            }
        }

        "applyChoices(licenseChoices)" should {
            "apply license choices on all licenses" {
                val choices = listOf(
                    SpdxLicenseChoice("$APACHE OR $MIT".toSpdx(), MIT.toSpdx()),
                    SpdxLicenseChoice("$MIT OR $GPL".toSpdx(), MIT.toSpdx()),
                    SpdxLicenseChoice("$BSD OR $GPL".toSpdx(), BSD.toSpdx())
                )

                val filteredResolvedLicenseInfo = RESOLVED_LICENSE_INFO.applyChoices(choices)

                filteredResolvedLicenseInfo.licenses should containLicensesExactly(MIT, BSD)
            }
        }
    }
}

private const val MIT = "MIT"
private const val APACHE = "Apache-2.0 WITH LLVM-exception"
private const val GPL = "GPL-2.0-only"
private const val BSD = "0BSD"

private val RESOLVED_LICENSE_INFO: ResolvedLicenseInfo by lazy {
    val resolvedLicenses = listOf(
        ResolvedLicense(
            license = APACHE.toSpdx() as SpdxSingleLicenseExpression,
            originalDeclaredLicenses = setOf("$APACHE OR $MIT"),
            originalExpressions = setOf(
                ResolvedOriginalExpression("$APACHE OR $MIT".toSpdx(), LicenseSource.DECLARED)
            ),
            locations = emptySet()
        ),
        ResolvedLicense(
            license = MIT.toSpdx() as SpdxSingleLicenseExpression,
            originalDeclaredLicenses = setOf("$APACHE OR $MIT"),
            originalExpressions = setOf(
                ResolvedOriginalExpression("$APACHE OR $MIT".toSpdx(), LicenseSource.DECLARED),
                ResolvedOriginalExpression("$MIT OR $GPL".toSpdx(), LicenseSource.DETECTED)
            ),
            locations = emptySet()
        ),
        ResolvedLicense(
            license = GPL.toSpdx() as SpdxSingleLicenseExpression,
            originalDeclaredLicenses = emptySet(),
            originalExpressions = setOf(
                ResolvedOriginalExpression("$MIT OR $GPL".toSpdx(), LicenseSource.DETECTED),
                ResolvedOriginalExpression("$BSD OR $GPL".toSpdx(), LicenseSource.CONCLUDED)
            ),
            locations = emptySet()
        ),
        ResolvedLicense(
            license = BSD.toSpdx() as SpdxSingleLicenseExpression,
            originalDeclaredLicenses = emptySet(),
            originalExpressions = setOf(
                ResolvedOriginalExpression("$BSD OR $GPL".toSpdx(), LicenseSource.CONCLUDED)
            ),
            locations = emptySet()
        )
    )

    ResolvedLicenseInfo(
        id = Identifier.EMPTY,
        licenseInfo = mockk(),
        licenses = resolvedLicenses,
        copyrightGarbage = emptyMap(),
        unmatchedCopyrights = emptyMap()
    )
}
