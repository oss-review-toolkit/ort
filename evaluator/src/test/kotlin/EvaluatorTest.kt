/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.utils.spdx.toSpdx

class EvaluatorTest : WordSpec() {
    init {
        "checkSyntax" should {
            "succeed if the script can be compiled" {
                val script = javaClass.getResource("/rules/no_gpl.rules.kts").readText()

                val result = Evaluator(ortResult).checkSyntax(script)

                result shouldBe true
            }

            "fail if the script can not be compiled" {
                val result = Evaluator(ortResult).checkSyntax(
                    """
                    broken script
                    """.trimIndent()
                )

                result shouldBe false
            }
        }

        "evaluate" should {
            "return no rule violations for an empty script" {
                val result = Evaluator(ortResult).run("")

                result.violations should beEmpty()
            }

            "be able to access the ORT result" {
                val result = Evaluator(ortResult).run(
                    """
                    require(ortResult.labels["label"] == "value") { "Failed to verify the ORT result." }
                    """.trimIndent()
                )

                result.violations should beEmpty()
            }

            "contain rule violations in the result" {
                val result = Evaluator(ortResult).run(
                    """
                    ruleViolations += RuleViolation(
                        rule = "rule 1",
                        pkg = Identifier("type:namespace:name:1.0"),
                        license = SpdxLicenseIdExpression("license-1"),
                        licenseSource = LicenseSource.DETECTED,
                        severity = Severity.ERROR,
                        message = "message 1",
                        howToFix = "how to fix 1"
                    )

                    ruleViolations += RuleViolation(
                        rule = "rule 2",
                        pkg = Identifier("type:namespace:name:2.0"),
                        license = SpdxLicenseIdExpression("license-2"),
                        licenseSource = LicenseSource.DECLARED,
                        severity = Severity.WARNING,
                        message = "message 2",
                        howToFix = "how to fix 2"
                    )
                    """.trimIndent()
                )

                result.violations should haveSize(2)

                with(result.violations[0]) {
                    rule shouldBe "rule 1"
                    pkg shouldBe Identifier("type:namespace:name:1.0")
                    license shouldBe "license-1".toSpdx()
                    licenseSource shouldBe LicenseSource.DETECTED
                    severity shouldBe Severity.ERROR
                    message shouldBe "message 1"
                    howToFix shouldBe "how to fix 1"
                }

                with(result.violations[1]) {
                    rule shouldBe "rule 2"
                    pkg shouldBe Identifier("type:namespace:name:2.0")
                    license shouldBe "license-2".toSpdx()
                    licenseSource shouldBe LicenseSource.DECLARED
                    severity shouldBe Severity.WARNING
                    message shouldBe "message 2"
                    howToFix shouldBe "how to fix 2"
                }
            }
        }
    }
}
