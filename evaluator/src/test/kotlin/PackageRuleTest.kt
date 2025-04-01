/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.evaluator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.licenses.ResolvedLicense
import org.ossreviewtoolkit.model.licenses.ResolvedOriginalExpression
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression.Strictness
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseIdExpression
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

class PackageRuleTest : WordSpec() {
    private fun createPackageRule(pkg: Package): PackageRule {
        val ruleSet = ruleSet(ortResult.addPackage(pkg))

        return PackageRule(
            ruleSet = ruleSet,
            name = "test",
            pkg = CuratedPackage(pkg),
            resolvedLicenseInfo = ruleSet.licenseInfoResolver.resolveLicenseInfo(pkg.id)
        )
    }

    private fun PackageRule.createLicenseRule(license: SpdxSingleLicenseExpression, licenseSource: LicenseSource) =
        LicenseRule(
            name = "test",
            resolvedLicense = resolvedLicenseInfo[license] ?: ResolvedLicense(
                license = license,
                originalDeclaredLicenses = emptySet(),
                originalExpressions = setOf(ResolvedOriginalExpression(license, licenseSource)),
                locations = emptySet()
            ),
            licenseSource = licenseSource
        )

    init {
        "hasLicense()" should {
            "return true if the package has concluded licenses" {
                val rule = createPackageRule(packageWithOnlyConcludedLicense)
                val matcher = rule.hasLicense()

                matcher.matches() shouldBe true
            }

            "return true if the package has declared licenses" {
                val rule = createPackageRule(packageWithOnlyDeclaredLicense)
                val matcher = rule.hasLicense()

                matcher.matches() shouldBe true
            }

            "return true if the package has detected licenses" {
                val rule = createPackageRule(packageWithOnlyDetectedLicense)
                val matcher = rule.hasLicense()

                matcher.matches() shouldBe true
            }

            "return false if the package has no license" {
                val rule = createPackageRule(packageWithoutLicense)
                val matcher = rule.hasLicense()

                matcher.matches() shouldBe false
            }

            "return false if the package has only 'not present' licenses" {
                val rule = createPackageRule(packageWithNotPresentLicense)
                val matcher = rule.hasLicense()

                matcher.matches() shouldBe false
            }

            "return false for non-existing packages" {
                val rule = createPackageRule(Package.EMPTY)
                val matcher = rule.hasLicense()

                matcher.matches() shouldBe false
            }
        }

        "hasConcludedLicense()" should {
            "return true if the concluded license is a license expression" {
                val rule = createPackageRule(packageWithConcludedLicense("MIT"))

                rule.hasConcludedLicense().matches() shouldBe true
            }

            "return true if the concluded license is ${SpdxConstants.NONE}" {
                val rule = createPackageRule(packageWithConcludedLicense(SpdxConstants.NONE))

                rule.hasConcludedLicense().matches() shouldBe true
            }

            "return false if the concluded license is ${SpdxConstants.NOASSERTION}" {
                val rule = createPackageRule(packageWithConcludedLicense(SpdxConstants.NOASSERTION))

                rule.hasConcludedLicense().matches() shouldBe false
            }

            "return false if the concluded license is null" {
                val rule = createPackageRule(packageWithConcludedLicense(null))

                rule.hasConcludedLicense().matches() shouldBe false
            }
        }

        "isExcluded()" should {
            "return true if the package is excluded" {
                val rule = createPackageRule(packageExcluded)
                val matcher = rule.isExcluded()

                matcher.matches() shouldBe true
            }

            "return false if the package is not excluded" {
                val rule = createPackageRule(packageWithoutLicense)
                val matcher = rule.isExcluded()

                matcher.matches() shouldBe false
            }
        }

        "isFromOrg()" should {
            "return true if the package is from org" {
                val rule = createPackageRule(packageWithoutLicense)
                val matcher = rule.isFromOrg("ossreviewtoolkit")

                matcher.matches() shouldBe true
            }

            "return false if the package is not from org" {
                val rule = createPackageRule(packageWithoutLicense)
                val matcher = rule.isFromOrg("unknown")

                matcher.matches() shouldBe false
            }
        }

        "isMetadataOnly()" should {
            "return true for a package that has only metadata" {
                val rule = createPackageRule(packageMetadataOnly)
                val matcher = rule.isMetadataOnly()

                matcher.matches() shouldBe true
            }

            "return false for a package that has not only metadata" {
                val rule = createPackageRule(packageWithoutLicense)
                val matcher = rule.isMetadataOnly()

                matcher.matches() shouldBe false
            }
        }

        "isProject()" should {
            "return true for a project" {
                val rule = createPackageRule(projectIncluded.toPackage())
                val matcher = rule.isProject()

                matcher.matches() shouldBe true
            }

            "return false for a package" {
                val rule = createPackageRule(packageWithoutLicense)
                val matcher = rule.isProject()

                matcher.matches() shouldBe false
            }
        }

        "isType()" should {
            "return true if the package has the provided type" {
                val rule = createPackageRule(packageWithoutLicense)
                val matcher = rule.isType("Maven")

                matcher.matches() shouldBe true
            }

            "return false if the package has not the provided type" {
                val rule = createPackageRule(packageWithoutLicense)
                val matcher = rule.isType("Gradle")

                matcher.matches() shouldBe false
            }
        }

        "isSpdxLicense()" should {
            "return true if the license is an SPDX license" {
                createPackageRule(packageWithoutLicense).apply {
                    val licenseRule = createLicenseRule(SpdxLicenseIdExpression("Apache-2.0"), LicenseSource.DECLARED)
                    val matcher = licenseRule.isSpdxLicense()

                    matcher.matches() shouldBe true
                }
            }

            "return false if the license is not an SPDX license" {
                createPackageRule(packageWithoutLicense).apply {
                    val licenseRule = createLicenseRule(SpdxLicenseIdExpression("invalid"), LicenseSource.DECLARED)
                    val matcher = licenseRule.isSpdxLicense()

                    matcher.matches() shouldBe false
                }
            }
        }

        "hasVulnerability()" should {
            "return true if any vulnerability is found" {
                val rule = createPackageRule(packageWithVulnerabilities)
                val matcher = rule.hasVulnerability()

                matcher.matches() shouldBe true
            }

            "return false if no vulnerabilities are found" {
                val rule = createPackageRule(packageWithOnlyDetectedLicense)
                val matcher = rule.hasVulnerability()

                matcher.matches() shouldBe false
            }

            "return true if a severity of a vulnerability is higher than the threshold" {
                val rule = createPackageRule(packageWithVulnerabilities)
                val matcher = rule.hasVulnerability(8.9f, "CVSS3")

                matcher.matches() shouldBe true
            }

            "return false if a severity of a vulnerability is lower than the threshold" {
                val rule = createPackageRule(packageWithVulnerabilities)
                val matcher = rule.hasVulnerability(9.1f, "CVSS3")

                matcher.matches() shouldBe false
            }

            "return true if a severity of a vulnerability is the same as the threshold" {
                val rule = createPackageRule(packageWithVulnerabilities)
                val matcher = rule.hasVulnerability(9.0f, "CVSS3")

                matcher.matches() shouldBe true
            }

            "return false if no vulnerability is found for the scoringSystem" {
                val rule = createPackageRule(packageWithVulnerabilities)
                val matcher = rule.hasVulnerability(10.0f, "fake-scoring-system")

                matcher.matches() shouldBe false
            }
        }
    }
}

private fun packageWithConcludedLicense(license: String?): Package =
    Package.EMPTY.copy(
        id = Identifier("Maven:some:package:0.0.1"),
        concludedLicense = license?.toSpdx(Strictness.ALLOW_ANY)
    )

private fun OrtResult.addPackage(pkg: Package): OrtResult {
    val analyzerResult = analyzer?.result ?: return this
    if (pkg in analyzerResult.packages) return this

    return copy(
        analyzer = analyzer!!.copy(
            result = analyzerResult.copy(
                packages = analyzerResult.packages + pkg
            )
        )
    )
}
