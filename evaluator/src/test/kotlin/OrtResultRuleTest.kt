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

package org.ossreviewtoolkit.evaluator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.PackageLicenseChoice
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.toSpdx

class OrtResultRuleTest : WordSpec({
    "getNonApplicablePackageLicenseChoices()" should {
        "return all corresponding SPDX license choices if the packageId does not exist" {
            val rule = createAnalyzerResult(
                "NPM::some-package:1.0.0" to "BSD-3-Clause OR MIT"
            ).setPackageLicenseChoices(
                "NPM::some-other-package:1.0.0" to listOf(
                    SpdxLicenseChoice(
                        choice = "BSD-3-Clause".toSpdx()
                    ),
                    SpdxLicenseChoice(
                        given = "A OR B".toSpdx(),
                        choice = "B".toSpdx()
                    )
                )
            ).createOrtResultRule()

            val choices = rule.getNonApplicablePackageLicenseChoices(LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED)

            choices.mapKeys { (id, _) -> id.toCoordinates() } shouldBe mapOf(
                "NPM::some-other-package:1.0.0" to setOf(
                    SpdxLicenseChoice(
                        choice = "BSD-3-Clause".toSpdx()
                    ),
                    SpdxLicenseChoice(
                        given = "A OR B".toSpdx(),
                        choice = "B".toSpdx()
                    )
                )
            )
        }

        "return only non-applicable SPDX license choices for packageId if the package id does exist" {
            val rule = createAnalyzerResult(
                "NPM::some-package:1.0.0" to "MIT OR Apache-2.0"
            ).setPackageLicenseChoices(
                "NPM::some-package:1.0.0" to listOf(
                    SpdxLicenseChoice(
                        choice = "BSD-3-Clause".toSpdx()
                    ),
                    SpdxLicenseChoice(
                        choice = "Apache-2.0".toSpdx()
                    ),
                    SpdxLicenseChoice(
                        given = "Apache-2.0 OR BSD-3-Clause".toSpdx(),
                        choice = "Apache-2.0".toSpdx()
                    ),
                    SpdxLicenseChoice(
                        given = "A OR B".toSpdx(),
                        choice = "B".toSpdx()
                    )
                )
            ).createOrtResultRule()

            val choices = rule.getNonApplicablePackageLicenseChoices(LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED)

            choices.mapKeys { (id, _) -> id.toCoordinates() } shouldBe mapOf(
                "NPM::some-package:1.0.0" to setOf(
                    SpdxLicenseChoice(
                        choice = "BSD-3-Clause".toSpdx()
                    ),
                    SpdxLicenseChoice(
                        given = "Apache-2.0 OR BSD-3-Clause".toSpdx(),
                        choice = "Apache-2.0".toSpdx()
                    ),
                    SpdxLicenseChoice(
                        given = "A OR B".toSpdx(),
                        choice = "B".toSpdx()
                    )
                )
            )
        }
    }

    "getNonApplicableRepositoryLicenseChoices()" should {
        "return all repository SPDX license choices which are not applicable" {
            val rule = createAnalyzerResult(
                "NPM::some-package:1.0.0" to "BSD-3-Clause OR MIT"
            ).setRepositoryLicenseChoices(
                SpdxLicenseChoice(
                    given = "BSD-3-Clause OR MIT".toSpdx(),
                    choice = "BSD-3-Clause".toSpdx()
                ),
                SpdxLicenseChoice(
                    given = "Apache-2.0 OR MIT".toSpdx(),
                    choice = "MIT".toSpdx()
                ),
                SpdxLicenseChoice(
                    given = "A OR B".toSpdx(),
                    choice = "B".toSpdx()
                )
            ).createOrtResultRule()

            val choices = rule.getNonApplicableRepositoryLicenseChoices(LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED)

            choices.shouldContainExactlyInAnyOrder(
                SpdxLicenseChoice(
                    given = "Apache-2.0 OR MIT".toSpdx(),
                    choice = "MIT".toSpdx()
                ),
                SpdxLicenseChoice(
                    given = "A OR B".toSpdx(),
                    choice = "B".toSpdx()
                )
            )
        }
    }
})

private fun createAnalyzerResult(vararg idsWithDeclaredLicenses: Pair<String, String>): OrtResult =
    OrtResult.EMPTY.copy(
        analyzer = AnalyzerRun.EMPTY.copy(
            result = AnalyzerResult.EMPTY.copy(
                projects = setOf(
                    Project.EMPTY.copy(
                        id = Identifier("NPM::project:0.0.1"),
                        scopeDependencies = setOf(
                            Scope(
                                name = "dependencies",
                                dependencies = idsWithDeclaredLicenses.mapTo(mutableSetOf()) { (id, _) ->
                                    PackageReference(
                                        id = Identifier(id)
                                    )
                                }
                            )
                        )
                    )
                ),
                packages = idsWithDeclaredLicenses.mapTo(mutableSetOf()) { (id, declaredLicense) ->
                    Package.EMPTY.copy(
                        id = Identifier(id),
                        declaredLicenses = setOf(declaredLicense),
                        declaredLicensesProcessed = DeclaredLicenseProcessor.process(setOf(declaredLicense))
                    )
                }
            )
        )
    )

private fun OrtResult.createOrtResultRule(): OrtResultRule =
    OrtResultRule(
        ruleSet = ruleSet(this),
        name = "rule"
    )

private fun OrtResult.setPackageLicenseChoices(vararg choices: Pair<String, List<SpdxLicenseChoice>>): OrtResult =
    setLicenseChoices(
        repository.config.licenseChoices.copy(
            packageLicenseChoices = choices.map {
                PackageLicenseChoice(
                    packageId = Identifier(it.first),
                    licenseChoices = it.second
                )
            }
        )
    )

private fun OrtResult.setRepositoryLicenseChoices(vararg choices: SpdxLicenseChoice): OrtResult =
    setLicenseChoices(
        repository.config.licenseChoices.copy(
            repositoryLicenseChoices = choices.toList()
        )
    )

private fun OrtResult.setLicenseChoices(licenseChoices: LicenseChoices): OrtResult =
    copy(
        repository = repository.copy(
            config = repository.config.copy(
                licenseChoices = licenseChoices
            )
        )
    )
