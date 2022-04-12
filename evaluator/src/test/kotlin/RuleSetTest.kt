/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.evaluator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should

import io.mockk.every
import io.mockk.spyk

import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

class RuleSetTest : WordSpec() {
    private val errorMessage = "error message"
    private val howToFix = "how to fix"

    init {
        "package rule" should {
            "add errors if it has no requirements" {
                val ruleSet = ruleSet(ortResult) {
                    packageRule("test") {
                        error(errorMessage, howToFix)
                    }
                }

                ruleSet.violations should haveSize(allPackages.size + allProjects.size)
            }

            "add errors only if all requirements are met" {
                val ruleSet = ruleSet(ortResult) {
                    packageRule("test") {
                        require {
                            +isExcluded()
                        }

                        error(errorMessage, howToFix)
                    }
                }

                ruleSet.violations should haveSize(2)
            }

            "add license errors only if all requirements are met" {
                val ruleSet = ruleSet(ortResult) {
                    packageRule("test") {
                        require {
                            -isExcluded()
                        }

                        licenseRule("test", LicenseView.ALL) {
                            require {
                                +isSpdxLicense()
                            }

                            error(errorMessage, howToFix)
                        }
                    }
                }

                ruleSet.violations should haveSize(6)
            }
        }

        "dependency rule" should {
            "add errors if it has no requirements" {
                val ruleSet = ruleSet(ortResult) {
                    dependencyRule("test") {
                        error(errorMessage, howToFix)
                    }
                }

                ruleSet.violations should haveSize(allPackages.size)
            }

            "add errors only if all requirements are met" {
                val ruleSet = ruleSet(ortResult) {
                    dependencyRule("test") {
                        require {
                            +isStaticallyLinked()
                        }

                        error(errorMessage, howToFix)
                    }
                }

                ruleSet.violations should haveSize(1)
            }

            "add license errors only if all requirements are met" {
                val ruleSet = ruleSet(ortResult) {
                    dependencyRule("test") {
                        require {
                            -isStaticallyLinked()
                        }

                        licenseRule("test", LicenseView.ALL) {
                            require {
                                +isSpdxLicense()
                            }

                            error(errorMessage, howToFix)
                        }
                    }
                }

                ruleSet.violations should haveSize(6)
            }

            "add no license errors if license is removed by package license choice in the correct order" {
                val ruleSet = ruleSet(ortResult) {
                    dependencyRule("test") {
                        licenseRule("test", LicenseView.ONLY_CONCLUDED) {
                            require {
                                +containsLicense("LicenseRef-b".toSpdx())
                            }

                            error(errorMessage, howToFix)
                        }
                    }
                }

                ruleSet.violations should haveSize(1)
            }

            "add no license errors if license is removed by repository license choice" {
                val ruleSet = ruleSet(ortResult) {
                    dependencyRule("test") {
                        licenseRule("test", LicenseView.ONLY_CONCLUDED) {
                            require {
                                +containsLicense("LicenseRef-c".toSpdx())
                            }

                            error(errorMessage, howToFix)
                        }
                    }
                }

                ruleSet.violations should beEmpty()
            }

            "use stable references as ancestor nodes" {
                val result = spyk(ortResult)
                val navigator = spyk(ortResult.dependencyNavigator)
                every { result.dependencyNavigator } returns navigator

                every { navigator.directDependencies(any(), any()) } answers {
                    ortResult.dependencyNavigator.directDependencies(firstArg(), secondArg()).map { node ->
                        val spyNode = spyk(node)
                        every { spyNode.getStableReference() } answers {
                            val ref = spyk(spyNode)
                            every { ref.id } answers { node.id.copy(name = node.id.name + "-ref") }
                            ref
                        }
                        spyNode
                    }
                }

                val ruleSet = ruleSet(result) {
                    dependencyRule("test") {
                        require {
                            -isStaticallyLinked()
                        }

                        if (ancestors.any { !it.id.name.endsWith("-ref") }) {
                            error("Node is not a reference.", howToFix)
                        }
                    }
                }

                ruleSet.violations should beEmpty()
            }
        }
    }

    private fun PackageRule.LicenseRule.containsLicense(expression: SpdxExpression) =
        object : RuleMatcher {
            override val description = "containsLicense(license)"

            override fun matches() = license == expression
        }
}
