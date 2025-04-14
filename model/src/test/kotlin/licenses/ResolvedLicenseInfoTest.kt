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

import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

@DelicateCoroutinesApi
class ResolvedLicenseInfoTest : WordSpec({
    "mainLicense()" should {
        "return declared and detected licenses, but no concluded license" {
            RESOLVED_LICENSE_INFO.mainLicense() shouldBe
                "($APACHE OR $MIT) AND ($MIT OR $GPL) AND ($BSD OR $GPL)".toSpdx()
        }
    }

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

        "execute in reasonable time for large license info with several OR operators".config(
            blockingTest = true,
            timeout = 2.seconds
        ) {
            runCancellable {
                COMPUTATION_HEAVY_RESOLVED_LICENSE_INFO.effectiveLicense(LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED)
            }
        }
    }

    "toExpression()" should {
        "execute in reasonable time for large license info with several OR operators".config(
            blockingTest = true,
            timeout = 2.seconds
        ) {
            runCancellable {
                COMPUTATION_HEAVY_RESOLVED_LICENSE_INFO.toExpression()
            }
        }
    }

    "applyChoices()" should {
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
})

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
            locations = setOf(
                ResolvedLicenseLocation(
                    provenance = UnknownProvenance,
                    location = TextLocation("LICENSE", TextLocation.UNKNOWN_LINE),
                    appliedCuration = null,
                    matchingPathExcludes = emptyList(),
                    copyrights = emptySet()
                )
            )
        ),
        ResolvedLicense(
            license = GPL.toSpdx() as SpdxSingleLicenseExpression,
            originalDeclaredLicenses = emptySet(),
            originalExpressions = setOf(
                ResolvedOriginalExpression("$MIT OR $GPL".toSpdx(), LicenseSource.DETECTED),
                ResolvedOriginalExpression("$BSD OR $GPL".toSpdx(), LicenseSource.CONCLUDED)
            ),
            locations = setOf(
                ResolvedLicenseLocation(
                    provenance = UnknownProvenance,
                    location = TextLocation("LICENCE", TextLocation.UNKNOWN_LINE),
                    appliedCuration = null,
                    matchingPathExcludes = emptyList(),
                    copyrights = emptySet()
                )
            )
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

/**
 * A (detected) resolved license info found in a real world scan, which makes several class members, for example
 * effectiveLicense(), rather computation-heavy.
 */
private val COMPUTATION_HEAVY_RESOLVED_LICENSE_INFO: ResolvedLicenseInfo by lazy {
    val licensesWithoutChoice = SpdxLicense.entries.subList(0, 200).map { SpdxExpression.parse(it.id) }

    // Expressions taken from a real world scan with swapped identifiers.
    val licensesWithChoice = listOf(
        "AAL OR Abstyles",
        "AdaCore-doc OR Adobe-2006",
        "Adobe-Display-PostScript OR Adobe-Glyph",
        "Adobe-Display-PostScript OR AAL",
        "Adobe-Utopia OR Adobe-2006",
        "Adobe-Display-PostScript OR Adobe-2006",
        "(Adobe-2006 OR AdaCore-doc) AND ADSL AND AFL-1.1 AND AFL-1.2 AND AFL-2.0 AND AFL-2.1",
        "AFL-3.0 OR Afmparse",
        "AGPL-1.0 OR Abstyles",
        "AFL-3.0 OR AGPL-1.0-only",
        "AGPL-1.0-or-later OR AGPL-3.0",
        "AAL OR Adobe-Glyph",
        "(AGPL-3.0-only OR AAL) AND AAL",
        "AGPL-3.0-or-later OR AGPL-1.0-or-later",
        "AAL OR AGPL-1.0-or-later",
        "AGPL-3.0-only OR Adobe-2006",
        "Adobe-Display-PostScript OR Aladdin",
        "AMDPLPA OR AFL-3.0",
        "AMD-newlib OR AAL",
        "AML OR AML OR AML OR AML OR AML OR AML",
        "AML-glslang OR AAL",
        "AMPAS OR ANTLR-PD",
        "AAL OR ANTLR-PD-fallback",
        "any-OSI OR AML-glslang",
        "Adobe-2006 OR AML-glslang"
    ).map { SpdxExpression.parse(it) }

    val licenseFindings = (licensesWithoutChoice + licensesWithChoice).mapTo(mutableSetOf()) { license ->
        LicenseFinding(
            license = license,
            location = TextLocation(
                path = "path",
                startLine = 1,
                endLine = 2
            )
        )
    }

    val licenseInfo = LicenseInfo(
        id = Identifier.EMPTY,
        declaredLicenseInfo = DeclaredLicenseInfo(
            authors = emptySet(),
            licenses = emptySet(),
            appliedCurations = emptyList(),
            processed = ProcessedDeclaredLicense(SpdxExpression.parse("NONE"))
        ),
        detectedLicenseInfo = DetectedLicenseInfo(
            findings = listOf(
                Findings(
                    provenance = UnknownProvenance,
                    licenses = licenseFindings,
                    copyrights = emptySet(),
                    licenseFindingCurations = emptyList(),
                    pathExcludes = emptyList(),
                    relativeFindingsPath = ""
                )
            )
        ),
        concludedLicenseInfo = ConcludedLicenseInfo(
            concludedLicense = null,
            appliedCurations = emptyList()
        )
    )

    val resolver = LicenseInfoResolver(
        provider = SimpleLicenseInfoProvider(listOf(licenseInfo)),
        copyrightGarbage = CopyrightGarbage(emptySet()),
        archiver = null,
        licenseFilePatterns = LicenseFilePatterns.DEFAULT,
        addAuthorsToCopyrights = false
    )

    resolver.resolveLicenseInfo(licenseInfo.id)
}

@DelicateCoroutinesApi
private suspend fun runCancellable(nonCancellableBlock: () -> Unit) {
    // Run non-cancellable operation in such a way that cancellation does not wait for completion, see also
    // https://github.com/Kotlin/kotlinx.coroutines/issues/1449#issuecomment-522907869.
    GlobalScope.async {
        nonCancellableBlock()
    }.await()
}
