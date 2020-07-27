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

package org.ossreviewtoolkit.evaluator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.licenses.LicenseView

import org.ossreviewtoolkit.model.utils.SimplePackageConfigurationProvider

class RuleSetTest : WordSpec() {
    private val errorMessage = "error message"
    private val howToFix = "how to fix"
    private val packageConfigurationProvider = SimplePackageConfigurationProvider()

    init {
        "package rule" should {
            "add errors if it has no requirements" {
                val ruleSet = ruleSet(ortResult, packageConfigurationProvider) {
                    packageRule("test") {
                        error(errorMessage, howToFix)
                    }
                }

                ruleSet.violations should haveSize(allPackages.size + allProjects.size)
            }

            "add errors only if all requirements are met" {
                val ruleSet = ruleSet(ortResult, packageConfigurationProvider) {
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
                val ruleSet = ruleSet(ortResult, packageConfigurationProvider) {
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

                ruleSet.violations should haveSize(4)
            }
        }

        "dependency rule" should {
            "add errors if it has no requirements" {
                val ruleSet = ruleSet(ortResult, packageConfigurationProvider) {
                    dependencyRule("test") {
                        error(errorMessage, howToFix)
                    }
                }

                ruleSet.violations should haveSize(allPackages.size)
            }

            "add errors only if all requirements are met" {
                val ruleSet = ruleSet(ortResult, packageConfigurationProvider) {
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
                val ruleSet = ruleSet(ortResult, packageConfigurationProvider) {
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

                ruleSet.violations should haveSize(4)
            }
        }
    }
}
