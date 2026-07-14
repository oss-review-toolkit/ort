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
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.Criticality

class ScorecardTest : StringSpec({
    val scorecard = Scorecard(config = ScorecardConfig(serverUrl = "https://api.securityscorecards.dev"))

    "determineValueCriticality should return the correct criticality" {
        scorecard.determineValueCriticality(1) shouldBe Criticality.Critical
        scorecard.determineValueCriticality(2) shouldBe Criticality.Critical
        scorecard.determineValueCriticality(3) shouldBe Criticality.High
        scorecard.determineValueCriticality(4) shouldBe Criticality.High
        scorecard.determineValueCriticality(5) shouldBe Criticality.Medium
        scorecard.determineValueCriticality(7) shouldBe Criticality.Medium
        scorecard.determineValueCriticality(8) shouldBe Criticality.Low
        scorecard.determineValueCriticality(10) shouldBe Criticality.Low
    }
})
