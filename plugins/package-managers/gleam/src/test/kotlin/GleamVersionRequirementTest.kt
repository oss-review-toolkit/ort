/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.gleam

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class GleamVersionRequirementTest : WordSpec({
    "convertHexVersionRequirement()" should {
        "convert ~> with major.minor to semver range" {
            convertHexVersionRequirement("~> 0.7") shouldBe ">=0.7.0 <1.0.0"
            convertHexVersionRequirement("~> 2.1") shouldBe ">=2.1.0 <3.0.0"
            convertHexVersionRequirement("~> 10.5") shouldBe ">=10.5.0 <11.0.0"
        }

        "convert ~> with major.minor.patch to semver range" {
            convertHexVersionRequirement("~> 0.7.3") shouldBe ">=0.7.3 <0.8.0"
            convertHexVersionRequirement("~> 2.1.5") shouldBe ">=2.1.5 <2.2.0"
            convertHexVersionRequirement("~> 1.0.0") shouldBe ">=1.0.0 <1.1.0"
        }

        "convert 'and' keyword to space" {
            convertHexVersionRequirement(">= 1.0.0 and < 2.0.0") shouldBe ">= 1.0.0 < 2.0.0"
            convertHexVersionRequirement(">= 1.0.0 AND < 2.0.0") shouldBe ">= 1.0.0 < 2.0.0"
        }

        "convert 'or' keyword to ||" {
            convertHexVersionRequirement(">= 1.0.0 or >= 3.0.0") shouldBe ">= 1.0.0 || >= 3.0.0"
            convertHexVersionRequirement(">= 1.0.0 OR >= 3.0.0") shouldBe ">= 1.0.0 || >= 3.0.0"
        }

        "handle combined ~> with and/or" {
            convertHexVersionRequirement("~> 1.0 and < 1.5.0") shouldBe ">=1.0.0 <2.0.0 < 1.5.0"
            convertHexVersionRequirement("~> 1.0 or ~> 2.0") shouldBe ">=1.0.0 <2.0.0 || >=2.0.0 <3.0.0"
        }

        "pass through standard operators unchanged" {
            convertHexVersionRequirement(">= 1.0.0") shouldBe ">= 1.0.0"
            convertHexVersionRequirement("< 2.0.0") shouldBe "< 2.0.0"
            convertHexVersionRequirement("== 1.5.0") shouldBe "== 1.5.0"
        }
    }
})
