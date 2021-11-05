/*
 * Copyright (C) 2021 Bosch.IO GmbH
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
import org.ossreviewtoolkit.model.licenses.TestUtils.containLicensesExactly
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.toSpdx

class ResolvedLicenseInfoTest : WordSpec() {
    private val mit = "MIT"
    private val apache = "Apache-2.0 WITH LLVM-exception"
    private val gpl = "GPL-2.0-only"
    private val bsd = "0BSD"

    init {
        "effectiveLicense()" should {
            "apply choices for LicenseView.ALL on all resolved licenses" {
                // All: (Apache-2.0 WITH LLVM-exception OR MIT) AND (MIT OR GPL-2.0-only) AND (0BSD OR GPL-2.0-only)
                val choices = listOf(
                    SpdxLicenseChoice("$apache OR $mit".toSpdx(), mit.toSpdx()),
                    SpdxLicenseChoice("$mit OR $gpl".toSpdx(), mit.toSpdx()),
                    SpdxLicenseChoice("$bsd OR $gpl".toSpdx(), bsd.toSpdx())
                )

                val effectiveLicense = createResolvedLicenseInfo().effectiveLicense(LicenseView.ALL, choices)

                effectiveLicense shouldBe "$mit AND $bsd".toSpdx()
            }

            "apply a choice for a sub-expression only" {
                // Declared: Apache-2.0 WITH LLVM-exception OR MIT OR GPL-2.0-only
                val resolvedLicenseInfo = ResolvedLicenseInfo(
                    id = Identifier.EMPTY,
                    licenseInfo = mockk(),
                    licenses = listOf(
                        ResolvedLicense(
                            license = apache.toSpdx() as SpdxSingleLicenseExpression,
                            originalDeclaredLicenses = setOf("$apache OR $mit"),
                            originalExpressions = setOf(
                                ResolvedOriginalExpression("$apache OR $mit OR $gpl".toSpdx(), LicenseSource.DECLARED)
                            ),
                            locations = emptySet()
                        )
                    ),
                    copyrightGarbage = emptyMap(),
                    unmatchedCopyrights = emptyMap()
                )

                val choices = listOf(
                    SpdxLicenseChoice("$apache OR $mit".toSpdx(), mit.toSpdx())
                )

                val effectiveLicense = resolvedLicenseInfo.effectiveLicense(LicenseView.ONLY_DECLARED, choices)

                effectiveLicense shouldBe "$mit OR $gpl".toSpdx()
            }

            "apply choices for LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED" {
                // Concluded: 0BSD OR GPL-2.0-only
                val choices = listOf(
                    SpdxLicenseChoice("$bsd OR $gpl".toSpdx(), bsd.toSpdx())
                )

                val effectiveLicense = createResolvedLicenseInfo().effectiveLicense(
                    LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED,
                    choices
                )

                effectiveLicense shouldBe bsd.toSpdx()
            }

            "apply choices for LicenseView.ONLY_DECLARED" {
                // Declared: Apache-2.0 WITH LLVM-exception OR MIT
                val choices = listOf(
                    SpdxLicenseChoice("$apache OR $mit".toSpdx(), mit.toSpdx())
                )

                val effectiveLicense = createResolvedLicenseInfo().effectiveLicense(
                    LicenseView.ONLY_DECLARED,
                    choices
                )

                effectiveLicense shouldBe mit.toSpdx()
            }

            "apply package and repository license choice for LicenseView.ONLY_CONCLUDED in the correct order" {
                val repositoryChoices = listOf(
                    SpdxLicenseChoice("$apache OR $mit".toSpdx(), mit.toSpdx()),
                    SpdxLicenseChoice("$bsd OR $gpl".toSpdx(), bsd.toSpdx())
                )
                val packageChoices = listOf(
                    SpdxLicenseChoice("$apache OR $mit".toSpdx(), apache.toSpdx())
                )

                val effectiveLicense = createResolvedLicenseInfo().effectiveLicense(
                    LicenseView.ALL,
                    packageChoices,
                    repositoryChoices
                )

                effectiveLicense shouldBe "$apache and ($mit or $gpl) and $bsd".toSpdx()
            }
        }

        "applyChoices(licenseChoices)" should {
            "apply license choices on all licenses" {
                val resolvedLicenseInfo = createResolvedLicenseInfo()

                val choices = listOf(
                    SpdxLicenseChoice("$apache OR $mit".toSpdx(), mit.toSpdx()),
                    SpdxLicenseChoice("$mit OR $gpl".toSpdx(), mit.toSpdx()),
                    SpdxLicenseChoice("$bsd OR $gpl".toSpdx(), bsd.toSpdx())
                )

                val filteredResolvedLicenseInfo = resolvedLicenseInfo.applyChoices(choices)

                filteredResolvedLicenseInfo.licenses should containLicensesExactly(mit, bsd)
            }
        }
    }

    private fun createResolvedLicenseInfo(): ResolvedLicenseInfo {
        val resolvedLicenses = listOf(
            ResolvedLicense(
                license = apache.toSpdx() as SpdxSingleLicenseExpression,
                originalDeclaredLicenses = setOf("$apache OR $mit"),
                originalExpressions = setOf(
                    ResolvedOriginalExpression("$apache OR $mit".toSpdx(), LicenseSource.DECLARED)
                ),
                locations = emptySet()
            ),
            ResolvedLicense(
                license = mit.toSpdx() as SpdxSingleLicenseExpression,
                originalDeclaredLicenses = setOf("$apache OR $mit"),
                originalExpressions = setOf(
                    ResolvedOriginalExpression("$apache OR $mit".toSpdx(), LicenseSource.DECLARED),
                    ResolvedOriginalExpression("$mit OR $gpl".toSpdx(), LicenseSource.DETECTED)
                ),
                locations = emptySet()
            ),
            ResolvedLicense(
                license = gpl.toSpdx() as SpdxSingleLicenseExpression,
                originalDeclaredLicenses = emptySet(),
                originalExpressions = setOf(
                    ResolvedOriginalExpression("$mit OR $gpl".toSpdx(), LicenseSource.DETECTED),
                    ResolvedOriginalExpression("$bsd OR $gpl".toSpdx(), LicenseSource.CONCLUDED)
                ),
                locations = emptySet()
            ),
            ResolvedLicense(
                license = bsd.toSpdx() as SpdxSingleLicenseExpression,
                originalDeclaredLicenses = emptySet(),
                originalExpressions = setOf(
                    ResolvedOriginalExpression("$bsd OR $gpl".toSpdx(), LicenseSource.CONCLUDED)
                ),
                locations = emptySet()
            )
        )

        return ResolvedLicenseInfo(
            id = Identifier.EMPTY,
            licenseInfo = mockk(),
            licenses = resolvedLicenses,
            copyrightGarbage = emptyMap(),
            unmatchedCopyrights = emptyMap()
        )
    }
}
