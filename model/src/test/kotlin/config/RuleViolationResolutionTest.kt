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

package org.ossreviewtoolkit.model.config

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity

class RuleViolationResolutionTest : WordSpec({
    "matches" should {
        "interpret the message as a regular expression" {
            resolution("message").matches(ruleViolation("message")) shouldBe true
            resolution(".*").matches(ruleViolation("message")) shouldBe true
            resolution("[a-zA-Z0-9]*").matches(ruleViolation("M3ss4GE")) shouldBe true

            resolution("").matches(ruleViolation("message")) shouldBe false
            resolution(".+").matches(ruleViolation("")) shouldBe false
            resolution("!(message)").matches(ruleViolation("message")) shouldBe false
        }

        "ignore white spaces" {
            resolution("Message with additional spaces. Another line.").matches(
                ruleViolation(
                    """
                        Message with  additional spaces. 
                        Another line.
                    """
                )
            ) shouldBe true
        }

        "ignore new lines" {
            resolution("Message with newline.").matches(ruleViolation("Message with\nnewline.")) shouldBe true
        }
    }
})

private fun resolution(message: String) =
    RuleViolationResolution(
        message = message,
        reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
        comment = ""
    )

private fun ruleViolation(message: String) =
    RuleViolation(
        rule = "",
        pkg = null,
        license = null,
        licenseSource = null,
        severity = Severity.ERROR,
        message = message,
        howToFix = ""
    )
