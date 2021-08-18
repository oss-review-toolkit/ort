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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.mockk

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason
import org.ossreviewtoolkit.model.licenses.TestUtils.containLicensesExactly
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.spdx.model.LicenseChoice
import org.ossreviewtoolkit.spdx.toSpdx

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
                    LicenseChoice("$apache OR $mit".toSpdx(), mit.toSpdx()),
                    LicenseChoice("$mit OR $gpl".toSpdx(), mit.toSpdx()),
                    LicenseChoice("$bsd OR $gpl".toSpdx(), bsd.toSpdx())
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
                            originalExpressions = mapOf(
                                LicenseSource.DECLARED to setOf("$apache OR $mit OR $gpl".toSpdx())
                            ),
                            locations = emptySet()
                        )
                    ),
                    copyrightGarbage = emptyMap(),
                    unmatchedCopyrights = emptyMap()
                )

                val choices = listOf(
                    LicenseChoice("$apache OR $mit".toSpdx(), mit.toSpdx())
                )

                val effectiveLicense = resolvedLicenseInfo.effectiveLicense(LicenseView.ONLY_DECLARED, choices)

                effectiveLicense shouldBe "$mit OR $gpl".toSpdx()
            }

            "apply choices for LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED" {
                // Concluded: 0BSD OR GPL-2.0-only
                val choices = listOf(
                    LicenseChoice("$bsd OR $gpl".toSpdx(), bsd.toSpdx())
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
                    LicenseChoice("$apache OR $mit".toSpdx(), mit.toSpdx())
                )

                val effectiveLicense = createResolvedLicenseInfo().effectiveLicense(
                    LicenseView.ONLY_DECLARED,
                    choices
                )

                effectiveLicense shouldBe mit.toSpdx()
            }

            "apply package and repository license choice for LicenseView.ONLY_CONCLUDED in the correct order" {
                val repositoryChoices = listOf(
                    LicenseChoice("$apache OR $mit".toSpdx(), mit.toSpdx()),
                    LicenseChoice("$bsd OR $gpl".toSpdx(), bsd.toSpdx())
                )
                val packageChoices = listOf(
                    LicenseChoice("$apache OR $mit".toSpdx(), apache.toSpdx())
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
                    LicenseChoice("$apache OR $mit".toSpdx(), mit.toSpdx()),
                    LicenseChoice("$mit OR $gpl".toSpdx(), mit.toSpdx()),
                    LicenseChoice("$bsd OR $gpl".toSpdx(), bsd.toSpdx())
                )

                val filteredResolvedLicenseInfo = resolvedLicenseInfo.applyChoices(choices)

                filteredResolvedLicenseInfo.licenses should containLicensesExactly(mit, bsd)
            }
        }

        "filterLicenseResolutions()" should {
            "remove license that is resolved by a license rule violation resolution" {
                val resolvedLicenseInfo = ResolvedLicenseInfo(
                    id = Identifier.EMPTY,
                    licenseInfo = mockk(),
                    licenses = listOf(
                        ResolvedLicense(
                            license = gpl.toSpdx() as SpdxSingleLicenseExpression,
                            originalDeclaredLicenses = setOf(gpl),
                            originalExpressions = mapOf(
                                LicenseSource.DECLARED to setOf(gpl.toSpdx()),
                                LicenseSource.DETECTED to setOf(gpl.toSpdx())
                            ),
                            locations = emptySet()
                        )
                    ),
                    copyrightGarbage = emptyMap(),
                    unmatchedCopyrights = emptyMap()
                )

                val violations = listOf(
                    RuleViolation(
                        "example rule",
                        Identifier("Maven", "org.oss-review-toolkit", "example", "0.0.1"),
                        SpdxSingleLicenseExpression.parse(gpl),
                        null,
                        Severity.ERROR,
                        "example message",
                        "how to fix"
                    )
                )

                val resolutions = listOf(
                    RuleViolationResolution(
                        "example message", RuleViolationResolutionReason.CANT_FIX_EXCEPTION, "example comment"
                    ),
                    RuleViolationResolution(
                        "different regex", RuleViolationResolutionReason.CANT_FIX_EXCEPTION, "example comment"
                    )
                )

                val filteredLicenses = resolvedLicenseInfo.licenses.filterLicenseResolutions(
                    Identifier("Maven", "org.oss-review-toolkit", "example", "0.0.1"),
                    violations,
                    resolutions
                )

                filteredLicenses should beEmpty()
            }

            "only remove license if it is the licenses of the specified package" {
                val resolvedLicenses = listOf(
                    ResolvedLicense(
                        license = gpl.toSpdx() as SpdxSingleLicenseExpression,
                        originalDeclaredLicenses = setOf(gpl),
                        originalExpressions = mapOf(
                            LicenseSource.DECLARED to setOf(gpl.toSpdx()),
                            LicenseSource.DETECTED to setOf(gpl.toSpdx())
                        ),
                        locations = emptySet()
                    ),
                    ResolvedLicense(
                        license = bsd.toSpdx() as SpdxSingleLicenseExpression,
                        originalDeclaredLicenses = setOf(bsd),
                        originalExpressions = mapOf(
                            LicenseSource.DECLARED to setOf(bsd.toSpdx()),
                            LicenseSource.DETECTED to setOf(bsd.toSpdx())
                        ),
                        locations = emptySet()
                    ),
                )

                val violations = listOf(
                    RuleViolation(
                        "example rule",
                        Identifier("Maven", "org.oss-review-toolkit", "example", "0.0.1"),
                        SpdxSingleLicenseExpression.parse(gpl),
                        null,
                        Severity.ERROR,
                        "example message",
                        "how to fix"
                    ),
                    RuleViolation(
                        "example rule",
                        Identifier("Maven", "org.oss-review-toolkit", "example", "0.0.10"),
                        SpdxSingleLicenseExpression.parse(bsd),
                        null,
                        Severity.ERROR,
                        "example message",
                        "how to fix"
                    ),
                )

                val resolutions = listOf(
                    RuleViolationResolution(
                        "example message", RuleViolationResolutionReason.CANT_FIX_EXCEPTION, "example comment"
                    ),
                    RuleViolationResolution(
                        "different regex", RuleViolationResolutionReason.CANT_FIX_EXCEPTION, "example comment"
                    )
                )

                val filteredLicenses = resolvedLicenses.filterLicenseResolutions(
                    Identifier("Maven", "org.oss-review-toolkit", "example", "0.0.1"),
                    violations,
                    resolutions
                )

                filteredLicenses should haveSize(1)
                filteredLicenses[0].license shouldBe SpdxSingleLicenseExpression.parse(bsd)
            }

            "not remove license that does not contain a license rule violation resolution" {
                val resolvedLicenses = listOf(
                    ResolvedLicense(
                        license = gpl.toSpdx() as SpdxSingleLicenseExpression,
                        originalDeclaredLicenses = setOf(gpl),
                        originalExpressions = mapOf(
                            LicenseSource.DECLARED to setOf(gpl.toSpdx()),
                            LicenseSource.DETECTED to setOf(gpl.toSpdx())
                        ),
                        locations = emptySet()
                    ),
                    ResolvedLicense(
                        license = bsd.toSpdx() as SpdxSingleLicenseExpression,
                        originalDeclaredLicenses = setOf(bsd),
                        originalExpressions = mapOf(
                            LicenseSource.DECLARED to setOf(bsd.toSpdx()),
                            LicenseSource.DETECTED to setOf(bsd.toSpdx())
                        ),
                        locations = emptySet()
                    ),
                )

                val violations = listOf(
                    RuleViolation(
                        "example rule",
                        Identifier("Maven", "org.oss-review-toolkit", "example", "0.0.1"),
                        SpdxSingleLicenseExpression.parse(gpl),
                        null,
                        Severity.ERROR,
                        "example message",
                        "how to fix"
                    ),
                    RuleViolation(
                        "example rule",
                        Identifier("Maven", "org.oss-review-toolkit", "example", "0.0.10"),
                        SpdxSingleLicenseExpression.parse(bsd),
                        null,
                        Severity.ERROR,
                        "example message",
                        "how to fix"
                    ),
                )

                val resolutions = listOf(
                    RuleViolationResolution(
                        "different regex", RuleViolationResolutionReason.CANT_FIX_EXCEPTION, "example comment"
                    )
                )

                val filteredLicenses = resolvedLicenses.filterLicenseResolutions(
                    Identifier("Maven", "org.oss-review-toolkit", "example", "0.0.1"),
                    violations,
                    resolutions
                )

                filteredLicenses should haveSize(2)
            }
        }
    }

    private fun createResolvedLicenseInfo(): ResolvedLicenseInfo {
        val resolvedLicenses = listOf(
            ResolvedLicense(
                license = apache.toSpdx() as SpdxSingleLicenseExpression,
                originalDeclaredLicenses = setOf("$apache OR $mit"),
                originalExpressions = mapOf(
                    LicenseSource.DECLARED to setOf("$apache OR $mit".toSpdx())
                ),
                locations = emptySet()
            ),
            ResolvedLicense(
                license = mit.toSpdx() as SpdxSingleLicenseExpression,
                originalDeclaredLicenses = setOf("$apache OR $mit"),
                originalExpressions = mapOf(
                    LicenseSource.DECLARED to setOf("$apache OR $mit".toSpdx()),
                    LicenseSource.DETECTED to setOf("$mit OR $gpl".toSpdx())
                ),
                locations = emptySet()
            ),
            ResolvedLicense(
                license = gpl.toSpdx() as SpdxSingleLicenseExpression,
                originalDeclaredLicenses = emptySet(),
                originalExpressions = mapOf(
                    LicenseSource.DETECTED to setOf("$mit OR $gpl".toSpdx()),
                    LicenseSource.CONCLUDED to setOf("$bsd OR $gpl".toSpdx())
                ),
                locations = emptySet()
            ),
            ResolvedLicense(
                license = bsd.toSpdx() as SpdxSingleLicenseExpression,
                originalDeclaredLicenses = emptySet(),
                originalExpressions = mapOf(
                    LicenseSource.CONCLUDED to setOf("$bsd OR $gpl".toSpdx())
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
