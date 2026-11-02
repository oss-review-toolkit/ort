/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
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
    "satisfies()" should {
        "correctly evaluate simple 'or-later' matching" {
            val orLaterRequirement = "GPL-2.0-or-later".toSpdx()
            val expression = "GPL-2.0-only".toSpdx()

            expression.satisfies(orLaterRequirement) shouldBe true
        }

        "correctly evaluate newer strict versions against 'or-later'" {
            val orLaterRequirement = "GPL-1.0-or-later".toSpdx()
            val newerVersion = "GPL-3.0-only".toSpdx()

            newerVersion.satisfies(orLaterRequirement) shouldBe true
        }

        "not be satisfied by older versions" {
            val newerRequirement = "GPL-3.0-or-later".toSpdx()
            val olderVersion = "GPL-2.0-only".toSpdx()

            olderVersion.satisfies(newerRequirement) shouldBe false
        }
    }

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
    }

    "applyChoice()" should {
        "successfully select a newer explicit version for an or-later expression" {
            val expression = "GPL-2.0-or-later".toSpdx()
            val choice = "GPL-3.0-only".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "GPL-3.0-only".toSpdx()
        }

        "successfully select a broader or-later version for an older or-later expression" {
            val expression = "GPL-1.0-or-later".toSpdx()
            val choice = "GPL-2.0-or-later".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "GPL-2.0-or-later".toSpdx()
        }

        "successfully resolve multi-choice compound expressions with evolution upgrades" {
            val expression = "GPL-2.0-or-later OR MIT".toSpdx()
            val choice = "GPL-3.0-only".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "GPL-3.0-only".toSpdx()
        }

        "throw an exception if the choice version is older than required by an or-later expression" {
            val expression = "GPL-3.0-or-later".toSpdx()
            val choice = "GPL-2.0-only".toSpdx()

            shouldThrow<InvalidLicenseChoiceException> { expression.applyChoice(choice) }
        }

        "throw an exception if the required expression demands a strict specific version without or-later modifier" {
            val expression = "GPL-2.0-only".toSpdx()
            val choice = "GPL-3.0-only".toSpdx()

            shouldThrow<InvalidLicenseChoiceException> { expression.applyChoice(choice) }
        }

        "throw an exception for cross-family license upgrades within choices" {
            val expression = "LGPL-2.0-or-later".toSpdx()
            val choice = "GPL-2.0-only".toSpdx()

            shouldThrow<InvalidLicenseChoiceException> { expression.applyChoice(choice) }
        }
    }

    "applyChoices()" should {
        "apply an evolution upgrade choice targeting an or-later sub-expression" {
            val expression = "(GPL-2.0-or-later OR MIT) AND Apache-2.0".toSpdx()
            val choices = listOf(SpdxLicenseChoice("GPL-2.0-or-later OR MIT".toSpdx(), "GPL-3.0-only".toSpdx()))

            val result = expression.applyChoices(choices)

            result shouldBe "GPL-3.0-only AND Apache-2.0".toSpdx()
        }

        "apply sequential evolution choices step by step across license versions" {
            val expression = "GPL-1.0-or-later OR MIT".toSpdx()
            val choices = listOf(
                SpdxLicenseChoice("GPL-1.0-or-later OR MIT".toSpdx(), "GPL-2.0-or-later".toSpdx()),
                SpdxLicenseChoice("GPL-2.0-or-later".toSpdx(), "GPL-3.0-only".toSpdx())
            )

            val result = expression.applyChoices(choices)

            result shouldBe "GPL-3.0-only".toSpdx()
        }
    }
})
