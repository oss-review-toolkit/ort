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

package org.ossreviewtoolkit.model.vulnerabilities

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class Cvss4RatingTest : StringSpec({
    "The CVSS 4 rating should be correct for a given score" {
        Cvss4Rating.fromScore(-0.1f) should beNull()

        Cvss4Rating.fromScore(0.0f) shouldBe Cvss4Rating.NONE

        Cvss4Rating.fromScore(0.1f) shouldBe Cvss4Rating.LOW
        Cvss4Rating.fromScore(3.9f) shouldBe Cvss4Rating.LOW

        Cvss4Rating.fromScore(4.0f) shouldBe Cvss4Rating.MEDIUM
        Cvss4Rating.fromScore(6.9f) shouldBe Cvss4Rating.MEDIUM

        Cvss4Rating.fromScore(7.0f) shouldBe Cvss4Rating.HIGH
        Cvss4Rating.fromScore(8.9f) shouldBe Cvss4Rating.HIGH

        Cvss4Rating.fromScore(9.0f) shouldBe Cvss4Rating.CRITICAL
        Cvss4Rating.fromScore(10.0f) shouldBe Cvss4Rating.CRITICAL

        Cvss4Rating.fromScore(10.1f) should beNull()
    }
})
