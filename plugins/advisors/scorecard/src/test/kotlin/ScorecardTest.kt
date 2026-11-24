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

package org.ossreviewtoolkit.plugins.advisors.scorecard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.net.URI

import org.ossreviewtoolkit.clients.scorecard.ScorecardCheck
import org.ossreviewtoolkit.clients.scorecard.ScorecardCheckDocumentation
import org.ossreviewtoolkit.model.Criticality
import org.ossreviewtoolkit.model.HealthMetric

class ScorecardTest : StringSpec({
    "determineValueCriticality should return the correct criticality" {
        determineValueCriticality(1) shouldBe Criticality.CRITICAL
        determineValueCriticality(2) shouldBe Criticality.CRITICAL
        determineValueCriticality(3) shouldBe Criticality.HIGH
        determineValueCriticality(4) shouldBe Criticality.HIGH
        determineValueCriticality(5) shouldBe Criticality.MEDIUM
        determineValueCriticality(7) shouldBe Criticality.MEDIUM
        determineValueCriticality(8) shouldBe Criticality.LOW
        determineValueCriticality(10) shouldBe Criticality.LOW
    }

    "toHealthMetric should describe the scale and direction of the score" {
        val check = ScorecardCheck(
            name = "Maintained",
            score = 7,
            reason = "15 commit(s) and 3 issue activity found in the last 90 days -- score normalized to 7",
            documentation = ScorecardCheckDocumentation(
                short = "Determines if the project is \"actively maintained\".",
                url = "https://example.org/checks.md#maintained"
            )
        )

        check.toHealthMetric() shouldBe HealthMetric(
            name = "Maintained",
            documentation = "Determines if the project is \"actively maintained\".",
            documentationLink = URI("https://example.org/checks.md#maintained"),
            minValue = 0.0,
            maxValue = 10.0,
            higherIsBetter = true,
            value = 7.0,
            criticality = Criticality.MEDIUM,
            reason = "15 commit(s) and 3 issue activity found in the last 90 days -- score normalized to 7"
        )
    }

    "toHealthMetric should keep a check that Scorecard could not compute" {
        val check = ScorecardCheck(name = "Signed-Releases", score = -1, reason = "no releases found")

        check.toHealthMetric() shouldBe HealthMetric(
            name = "Signed-Releases",
            minValue = 0.0,
            maxValue = 10.0,
            higherIsBetter = true,
            value = null,
            criticality = null,
            reason = "no releases found"
        )
    }

    "toHealthMetric should return null for a check without a name" {
        ScorecardCheck(score = 7).toHealthMetric() should beNull()
    }
})
