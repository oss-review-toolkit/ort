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

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class DependencyRuleTest : WordSpec() {
    private val ruleSet = RuleSet(ortResult)

    init {
        "isAtTreeLevel()" should {
            "return true if the dependency is at the expected tree level" {
                val rule = DependencyRule(
                    ruleSet,
                    "test",
                    packageWithoutLicense,
                    emptyList(),
                    packageWithoutLicense.toReference(),
                    emptyList(),
                    0,
                    scopeIncluded,
                    projectIncluded
                )
                val matcher = rule.isAtTreeLevel(0)

                matcher.matches() shouldBe true
            }

            "return false if the dependency is not at the expected tree level" {
                val rule = DependencyRule(
                    ruleSet,
                    "test",
                    packageWithoutLicense,
                    emptyList(),
                    packageWithoutLicense.toReference(),
                    emptyList(),
                    0,
                    scopeIncluded,
                    projectIncluded
                )
                val matcher = rule.isAtTreeLevel(1)

                matcher.matches() shouldBe false
            }
        }

        "isProjectFromOrg()" should {
            "return true if the project is from org" {
                val rule = DependencyRule(
                    ruleSet,
                    "test",
                    packageWithoutLicense,
                    emptyList(),
                    packageWithoutLicense.toReference(),
                    emptyList(),
                    0,
                    scopeIncluded,
                    projectIncluded
                )
                val matcher = rule.isProjectFromOrg("here")

                matcher.matches() shouldBe true
            }

            "return false if the project is not from org" {
                val rule = DependencyRule(
                    ruleSet,
                    "test",
                    packageWithoutLicense,
                    emptyList(),
                    packageWithoutLicense.toReference(),
                    emptyList(),
                    0,
                    scopeIncluded,
                    projectIncluded
                )
                val matcher = rule.isProjectFromOrg("unknown")

                matcher.matches() shouldBe false
            }
        }

        "isStaticallyLinked()" should {
            "return true if the dependency is statically linked" {
                val rule = DependencyRule(
                    ruleSet,
                    "test",
                    packageStaticallyLinked,
                    emptyList(),
                    packageRefStaticallyLinked,
                    emptyList(),
                    0,
                    scopeIncluded,
                    projectIncluded
                )
                val matcher = rule.isStaticallyLinked()

                matcher.matches() shouldBe true
            }

            "return false if the dependency is not statically linked" {
                val rule = DependencyRule(
                    ruleSet,
                    "test",
                    packageDynamicallyLinked,
                    emptyList(),
                    packageRefDynamicallyLinked,
                    emptyList(),
                    0,
                    scopeIncluded,
                    projectIncluded
                )
                val matcher = rule.isStaticallyLinked()

                matcher.matches() shouldBe false
            }
        }
    }
}
