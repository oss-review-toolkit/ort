/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.evaluator

import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.specs.WordSpec

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

                println("issues:\n${ruleSet.violations}")
                ruleSet.violations should haveSize(2)
            }

            "add license errors only if all requirements are met" {
                val ruleSet = ruleSet(ortResult) {
                    packageRule("test") {
                        require {
                            -isExcluded()
                        }

                        licenseRule("test", LicenseView.All) {
                            require {
                                +isSpdxLicense()
                            }

                            error(errorMessage, howToFix)
                        }
                    }
                }

                ruleSet.violations should haveSize(1)
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

                        licenseRule("test", LicenseView.All) {
                            require {
                                +isSpdxLicense()
                            }

                            error(errorMessage, howToFix)
                        }

                    }
                }

                ruleSet.violations should haveSize(1)
            }
        }
    }
}
