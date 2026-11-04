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

package org.ossreviewtoolkit.utils.spdxexpression

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class SpdxExpressionOrLaterTest : WordSpec({
    "isValidChoice()" should {
        "allow selecting a concrete license version for an or-later requirement" {
            val expression = "GPL-2.0-or-later".toSpdx()
            val choice = "GPL-2.0-only".toSpdx()

            expression.isValidChoice(choice) shouldBe true
        }

        "return true if a choice satisfies a broader or-later condition in compound contexts" {
            val spdxExpression = "GPL-2.0-or-later AND MIT".toSpdx()

            spdxExpression.isValidChoice("GPL-3.0-only AND MIT".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("GPL-2.0-or-later AND MIT".toSpdx()) shouldBe true
        }

        "return false if an evolutionary upgrade choice drops mandatory parallel AND components" {
            val spdxExpression = "GPL-2.0-or-later AND MIT".toSpdx()

            spdxExpression.isValidChoice("GPL-3.0-only".toSpdx()) shouldBe false
        }

        "allow evolutionary upgrades with identical WITH exception (no OR)" {
            val expression = "GPL-2.0-or-later WITH Classpath-exception-2.0".toSpdx()
            val choice = "GPL-3.0-only WITH Classpath-exception-2.0".toSpdx()

            expression.isValidChoice(choice) shouldBe true
        }

        "reject evolutionary upgrades if the WITH exception changes or is dropped (no OR)" {
            val expression = "GPL-2.0-or-later WITH Classpath-exception-2.0".toSpdx()
            val mismatchChoice = "GPL-3.0-only WITH Universal-FOSS-Exception-1.0".toSpdx()
            val droppedChoice = "GPL-3.0-only".toSpdx()

            expression.isValidChoice(mismatchChoice) shouldBe false
            expression.isValidChoice(droppedChoice) shouldBe false
        }

        "allow linear evolutionary upgrades for pure or-later identifiers (no WITH, no OR)" {
            val expression = "GPL-1.0-or-later".toSpdx()

            expression.isValidChoice("GPL-2.0-only".toSpdx()) shouldBe true
            expression.isValidChoice("GPL-3.0-or-later".toSpdx()) shouldBe true
        }

        "reject invalid linear upgrades for pure or-later identifiers (no WITH, no OR)" {
            val expression = "GPL-2.0-or-later".toSpdx()

            expression.isValidChoice("GPL-1.0-only".toSpdx()) shouldBe false
            expression.isValidChoice("LGPL-2.1-only".toSpdx()) shouldBe false // Falsche Familie
        }

        "allow matching an upgraded version containing a WITH operator inside an OR choice" {
            val expression = "GPL-2.0-or-later WITH Classpath-exception-2.0 OR MIT".toSpdx()
            val choice = "GPL-3.0-only WITH Classpath-exception-2.0".toSpdx()

            expression.isValidChoice(choice) shouldBe true
        }

        "allow resolving evolutionary upgrades inside complex nested WITH, OR, and AND configurations" {
            val expression = "(GPL-2.0-or-later WITH Classpath-exception-2.0 OR BSD-3-Clause) AND Apache-2.0".toSpdx()
            val validChoice = "GPL-3.0-only WITH Classpath-exception-2.0 AND Apache-2.0".toSpdx()

            expression.isValidChoice(validChoice) shouldBe true
        }

        "reject complex nested choices if mandatory parallel AND elements are missing" {
            val expression = "(GPL-2.0-or-later WITH Classpath-exception-2.0 OR BSD-3-Clause) AND Apache-2.0".toSpdx()
            val invalidChoice = "GPL-3.0-only WITH Classpath-exception-2.0".toSpdx() // Apache-2.0 fehlt

            expression.isValidChoice(invalidChoice) shouldBe false
        }
    }

    "applyChoice()" should {
        "apply a choice for a single or-later license" {
            val expression = "GPL-2.0-or-later".toSpdx()
            val choice = "GPL-3.0-only".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "GPL-3.0-only".toSpdx()
        }

        "apply a choice for a single or-later license to a later version with or-later" {
            val expression = "GPL-1.0-or-later".toSpdx()
            val choice = "GPL-2.0-or-later".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "GPL-2.0-or-later".toSpdx()
        }

        "apply a choice for an or-later license within a compound OR expression" {
            val expression = "GPL-2.0-or-later OR MIT OR Apache-2.0".toSpdx()
            val choice = "GPL-2.0-only OR MIT OR Apache-2.0".toSpdx()

            val result = expression.applyChoice(choice)

            expression.isValidChoice(choice) shouldBe true
            result shouldBe "GPL-2.0-only OR MIT OR Apache-2.0".toSpdx()
        }

        "apply a choice for an or-later license and an OR at the same time" {
            val expression = "GPL-2.0-or-later OR MIT".toSpdx()
            val choice = "GPL-3.0-only".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "GPL-3.0-only".toSpdx()
        }

        "apply a choice for an or-later license within a compound AND expression" {
            val expression = "0BSD AND GPL-2.0-or-later AND MIT".toSpdx()
            val given = "GPL-2.0-or-later".toSpdx()
            val choice = "GPL-3.0-only".toSpdx()

            val result = expression.applyChoice(choice, given)

            result shouldBe "0BSD AND GPL-3.0-only AND MIT".toSpdx()
        }

        "throw an exception if the chosen version is older" {
            val expression = "GPL-3.0-or-later".toSpdx()
            val choice = "GPL-2.0-only".toSpdx()

            shouldThrow<InvalidLicenseChoiceException> { expression.applyChoice(choice) }
        }

        "throw an exception if a later version is chosen for an -only license" {
            val expression = "GPL-2.0-only".toSpdx()
            val choice = "GPL-3.0-only".toSpdx()

            shouldThrow<InvalidLicenseChoiceException> { expression.applyChoice(choice) }
        }

        "throw an exception if the version choice is ok, but the license ID differs" {
            val expression = "LGPL-2.0-or-later".toSpdx()
            val choice = "GPL-2.0-only".toSpdx()

            shouldThrow<InvalidLicenseChoiceException> { expression.applyChoice(choice) }
        }

        "apply an or-later choice to an expression containing WITH" {
            val expression = "GPL-2.0-or-later WITH Classpath-exception-2.0".toSpdx()
            val choice = "GPL-3.0-only WITH Classpath-exception-2.0".toSpdx()

            expression.applyChoice(choice) shouldBe "GPL-3.0-only WITH Classpath-exception-2.0".toSpdx()
        }

        "apply an or-later choice to an expression containing WITH and OR" {
            val expression = "GPL-2.0-or-later WITH Classpath-exception-2.0 OR MIT".toSpdx()
            val choice = "GPL-3.0-only WITH Classpath-exception-2.0".toSpdx()

            expression.applyChoice(choice) shouldBe "GPL-3.0-only WITH Classpath-exception-2.0".toSpdx()
        }
    }

    "applyChoices()" should {
        "apply an or-later choice for a single or-later license" {
            val expression = "GPL-2.0-or-later".toSpdx()
            val choices = listOf(SpdxLicenseChoice(choice = "GPL-3.0-only".toSpdx()))

            val result = expression.applyChoices(choices)

            result shouldBe "GPL-3.0-only".toSpdx()
        }

        "apply an or-later choice for an expression without any OR" {
            val expression = "MIT AND GPL-2.0-or-later".toSpdx()
            val choices = listOf(SpdxLicenseChoice(choice = "MIT AND GPL-3.0-only".toSpdx()))

            val result = expression.applyChoices(choices)

            result shouldBe "MIT AND GPL-3.0-only".toSpdx()
        }

        "apply an or-later choice for a sub-expression without any OR" {
            val expression = "0BSD AND GPL-2.0-or-later AND MIT".toSpdx()
            val choices = listOf(SpdxLicenseChoice("GPL-2.0-or-later".toSpdx(), "GPL-3.0-only".toSpdx()))

            val result = expression.applyChoices(choices)

            result shouldBe "0BSD AND GPL-3.0-only AND MIT".toSpdx()
        }

        "apply a choice for or-later and OR to a sub-expression" {
            val expression = "(GPL-2.0-or-later OR MIT) AND Apache-2.0".toSpdx()
            val choices = listOf(SpdxLicenseChoice("GPL-2.0-or-later OR MIT".toSpdx(), "GPL-3.0-only".toSpdx()))

            val result = expression.applyChoices(choices)

            result shouldBe "GPL-3.0-only AND Apache-2.0".toSpdx()
        }

        "apply multiple or-later choices as expected" {
            val expression = "GPL-1.0-or-later OR MIT".toSpdx()
            val choices = listOf(
                SpdxLicenseChoice("GPL-1.0-or-later OR MIT".toSpdx(), "GPL-2.0-or-later".toSpdx()),
                SpdxLicenseChoice("GPL-2.0-or-later".toSpdx(), "GPL-3.0-only".toSpdx())
            )

            val result = expression.applyChoices(choices)

            result shouldBe "GPL-3.0-only".toSpdx()
        }

        "apply or-later and OR choice to an expression containing WITH, OR, and AND" {
            val expression = "(GPL-2.0-or-later WITH Classpath-exception-2.0 OR BSD-3-Clause) AND Apache-2.0".toSpdx()
            val choices = listOf(
                SpdxLicenseChoice(
                    "GPL-2.0-or-later WITH Classpath-exception-2.0 OR BSD-3-Clause".toSpdx(),
                    "GPL-3.0-only WITH Classpath-exception-2.0".toSpdx()
                )
            )

            val result = expression.applyChoices(choices)

            result shouldBe "GPL-3.0-only WITH Classpath-exception-2.0 AND Apache-2.0".toSpdx()
        }
    }
})
