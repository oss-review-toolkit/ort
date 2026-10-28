/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.milliseconds

class SpdxExpressionChoiceTest : WordSpec({
    "applyChoice()" should {
        "return the choice for a simple expression" {
            val expression = "A".toSpdx()
            val choice = "A".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "A".toSpdx()
        }

        "throw an exception if a choice picks a license which is not part of the expression" {
            val expression = "A".toSpdx()
            val choice = "B".toSpdx()

            shouldThrow<InvalidLicenseChoiceException> { expression.applyChoice(choice) }
        }

        "return the new expression if only a part of the expression is matched by the subExpression" {
            val expression = "A OR B OR C".toSpdx()
            val choice = "B".toSpdx()
            val subExpression = "A OR B".toSpdx()

            val result = expression.applyChoice(choice, subExpression)

            result shouldBe "B OR C".toSpdx()
        }

        "work with choices that itself are a choice" {
            val expression = "A OR B OR C OR D".toSpdx()
            val choice = "A OR B".toSpdx()
            val subExpression = "A OR B OR C".toSpdx()

            val result = expression.applyChoice(choice, subExpression)

            result shouldBe "A OR B OR D".toSpdx()
        }

        "apply the choice if the expression contains multiple choices" {
            val expression = "A OR B OR C".toSpdx()
            val choice = "B".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "B".toSpdx()
        }

        "throw an exception if the chosen license is not a valid option" {
            val expression = "A OR B".toSpdx()
            val choice = "C".toSpdx()

            shouldThrow<InvalidLicenseChoiceException> { expression.applyChoice(choice) }
        }

        "apply the choice even if not literally contained in the expression" {
            val expression = "(A OR B) AND C".toSpdx()
            val choice = "A AND C".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "A AND C".toSpdx()
        }

        "return the reduced subExpression if the choice was valid" {
            val expression = "(A OR B) AND C AND (D OR E)".toSpdx()
            val choice = "A AND C AND D".toSpdx()
            val subExpression = "A AND C AND D OR A AND C AND E".toSpdx()

            val result = expression.applyChoice(choice, subExpression)

            result shouldBe "A AND C AND D OR B AND C AND D OR B AND C AND E".toSpdx()
        }

        "throw an exception if the subExpression does not match the simple expression" {
            val expression = "A".toSpdx()
            val choice = "X".toSpdx()
            val subExpression = "X OR Y".toSpdx()

            shouldThrow<InvalidSubExpressionException> { expression.applyChoice(choice, subExpression) }
        }

        "throw an exception if the subExpression does not match the expression" {
            val expression = "A OR B OR C".toSpdx()
            val choice = "X".toSpdx()
            val subExpression = "X OR Y OR Z".toSpdx()

            shouldThrow<InvalidSubExpressionException> { expression.applyChoice(choice, subExpression) }
        }

        "throw an exception if the subExpression does not match" {
            val expression = "(A OR B) AND C AND (D OR E)".toSpdx()
            val choice = "A AND C AND D".toSpdx()
            val subExpression = "(A AND C AND D) OR (X AND Y AND Z)".toSpdx()

            shouldThrow<InvalidSubExpressionException> { expression.applyChoice(choice, subExpression) }
        }

        "resolve the choice for a sub-expression in a compound expression" {
            val expression = "(CDDL-1.1 OR GPL-2.0-only) AND (CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0)"
                .toSpdx()
            val choice = "CDDL-1.1".toSpdx()
            val subExpression = "CDDL-1.1 OR GPL-2.0-only".toSpdx()

            val result = expression.applyChoice(choice, subExpression)

            result shouldBe "CDDL-1.1 AND (CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0)".toSpdx()
        }
    }

    "applyChoices()" should {
        "return the correct result if a single choice is applied" {
            val expression = "A OR B OR C OR D".toSpdx()

            val choices = listOf(SpdxLicenseChoice(expression, "A".toSpdx()))

            val result = expression.applyChoices(choices)

            result shouldBe "A".toSpdx()
        }

        "return the correct result if multiple simple choices are applied" {
            val expression = "(A OR B) AND (C OR D)".toSpdx()

            val choices = listOf(
                SpdxLicenseChoice("A OR B".toSpdx(), "A".toSpdx()),
                SpdxLicenseChoice("C OR D".toSpdx(), "C".toSpdx())
            )

            val result = expression.applyChoices(choices)

            result shouldBe "A AND C".toSpdx()
        }

        "ignore invalid sub-expressions and return the correct result for valid choices" {
            val expression = "A OR B OR C OR D".toSpdx()

            val choices = listOf(
                SpdxLicenseChoice("A OR B".toSpdx(), "B".toSpdx()), // B OR C OR D
                SpdxLicenseChoice("A OR C".toSpdx(), "A".toSpdx()) // not applied
            )

            val result = expression.applyChoices(choices)

            result shouldBe "B OR C OR D".toSpdx()
        }

        "apply the second choice to the effective license after the first choice" {
            val expression = "A OR B OR C OR D".toSpdx()

            val choices = listOf(
                SpdxLicenseChoice("A OR B".toSpdx(), "B".toSpdx()), // B OR C OR D
                SpdxLicenseChoice("B OR C".toSpdx(), "B".toSpdx()) // B OR D
            )

            val result = expression.applyChoices(choices)

            result shouldBe "B OR D".toSpdx()
        }

        "apply a single choice to multiple expressions" {
            val expression = "(A OR B) AND (C OR D) AND (A OR E)".toSpdx()

            val choices = listOf(
                SpdxLicenseChoice("A OR B".toSpdx(), "A".toSpdx()),
                SpdxLicenseChoice("A OR E".toSpdx(), "A".toSpdx())
            )

            val result = expression.applyChoices(choices)

            result shouldBe "A AND (C OR D) AND A".toSpdx()
        }

        "given expressions should match semantically equivalent license expressions" {
            val expression = "A OR B".toSpdx()

            val choices = listOf(
                SpdxLicenseChoice("B OR A".toSpdx(), "A".toSpdx())
            )

            val result = expression.applyChoices(choices)

            result shouldBe "A".toSpdx()
        }

        "apply choices regardless of license order in an OR-expression" {
            val expression = "(GPL-2.0-only OR MIT) AND Apache-2.0".toSpdx()
            val choicesGivenGplOrMit = listOf(SpdxLicenseChoice("GPL-2.0-only OR MIT".toSpdx(), "MIT".toSpdx()))
            val choicesGivenMitOrGpl = listOf(SpdxLicenseChoice("MIT OR GPL-2.0-only".toSpdx(), "MIT".toSpdx()))

            val resultGivenGplOrMit = expression.applyChoices(choicesGivenGplOrMit)
            val resultGivenMitOrGpl = expression.applyChoices(choicesGivenMitOrGpl)

            resultGivenGplOrMit shouldBe "MIT AND Apache-2.0".toSpdx()
            resultGivenMitOrGpl shouldBe "MIT AND Apache-2.0".toSpdx()
        }

        "resolve the choice for a sub-expression in a compound expression" {
            // This test ensures that when applying a choice to a sub-expression that appears multiple times,
            // only the matching occurrence is replaced, not similar-looking expressions with WITH-operands.
            // This prevents the over-simplification bug where string replacement would affect too much.
            val expression = "(CDDL-1.1 OR GPL-2.0-only) AND (CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0)"
                .toSpdx()
            val choices = listOf(SpdxLicenseChoice("CDDL-1.1 OR GPL-2.0-only".toSpdx(), "CDDL-1.1".toSpdx()))

            val result = expression.applyChoices(choices)

            // The second OR-expression should remain unchanged because it has a WITH-operand and thus does not exactly
            // match the sub-expression despite containing a sub-string of it.
            result shouldBe "CDDL-1.1 AND (CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0)".toSpdx()
        }
    }

    "isSubExpression()" should {
        "return true for the same simple expression" {
            val mit = "MIT".toSpdx() as SpdxSimpleExpression

            mit.isSubExpression(mit) shouldBe true
        }

        "return true for the same single expression" {
            val mit = "GPL-2.0-only WITH Classpath-exception-2.0".toSpdx() as SpdxSingleLicenseExpression

            mit.isSubExpression(mit) shouldBe true
        }

        "return true for the same compound expression" {
            val mit = "CDDL-1.1 OR GPL-2.0-only".toSpdx() as SpdxCompoundExpression

            mit.isSubExpression(mit) shouldBe true
        }

        "work correctly for compound expressions with exceptions" {
            val gplWithException = "CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0".toSpdx()
            val gpl = "CDDL-1.1 OR GPL-2.0-only".toSpdx()

            gplWithException.isSubExpression(gpl) shouldBe false
        }

        "work correctly for nested compound expressions" {
            val expression = "(CDDL-1.1 OR GPL-2.0-only) AND (CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0)"
                .toSpdx()
            val subExpression = "CDDL-1.1 OR GPL-2.0-only".toSpdx()

            expression.isSubExpression(subExpression) shouldBe true
        }
    }

    "isValidChoice()" should {
        "return true if a choice is valid" {
            val spdxExpression = "(A OR B) AND C AND (D OR E)".toSpdx()

            spdxExpression.isValidChoice("A AND C AND D".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("A AND D AND C".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("C AND A AND D".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("C AND D AND A".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("D AND A AND C".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("D AND C AND A".toSpdx()) shouldBe true

            spdxExpression.isValidChoice("A AND C AND E".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("B AND C AND D".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("B AND C AND E".toSpdx()) shouldBe true
        }

        "return false if a choice is invalid" {
            val spdxExpression = "(A OR B) AND C AND (D OR E)".toSpdx()

            spdxExpression.isValidChoice("A".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("A AND B".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("A AND C".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("A AND D".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("A AND E".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("A AND B AND C".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("A AND B AND D".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("A AND B AND C AND D".toSpdx()) shouldBe false
        }

        "return true for a simplified choice for a complex expression" {
            val license = "(A OR B) AND (A OR C OR D) AND (A OR C OR B)"

            license.toSpdx().isValidChoice("A".toSpdx()) shouldBe true
        }
    }

    "offersChoice()" should {
        "return true if the expression contains the OR operator" {
            "A OR B".toSpdx().offersChoice() shouldBe true
            "A AND B OR C".toSpdx().offersChoice() shouldBe true
            "A OR B AND C".toSpdx().offersChoice() shouldBe true
            "A AND B AND C OR D".toSpdx().offersChoice() shouldBe true
        }

        "return false if the expression does not contain the OR operator" {
            "A".toSpdx().offersChoice() shouldBe false
            "A AND B".toSpdx().offersChoice() shouldBe false
            "A AND B AND C".toSpdx().offersChoice() shouldBe false
        }

        "return false if the expression contains the OR operator with equal operands" {
            "A OR A".toSpdx().offersChoice() shouldBe false
        }
    }

    "validChoices()" should {
        "return the original terms of an expression in DNF" {
            val choices = "(A AND B) OR (C AND D) OR (E AND F)".toSpdx().validChoices()

            choices.map { it.toString() } should containExactlyInAnyOrder(
                "A AND B",
                "C AND D",
                "E AND F"
            )
        }

        "return the distribution of all terms of an expression in CNF" {
            val choices = "(A OR B) AND (C OR D)".toSpdx().validChoices()

            choices.map { it.toString() } should containExactlyInAnyOrder(
                "A AND C",
                "A AND D",
                "B AND C",
                "B AND D"
            )
        }

        "return the valid choices for a complex expression" {
            val choices = "(A OR B) AND C AND (D OR E)".toSpdx().validChoices()

            choices.map { it.toString() } should containExactlyInAnyOrder(
                "A AND C AND D",
                "A AND C AND E",
                "B AND C AND D",
                "B AND C AND E"
            )
        }

        "return the valid choices for a nested expression" {
            val choices = "(A OR (B AND (C OR D))) AND (E OR F)".toSpdx().validChoices()

            choices.map { it.toString() } should containExactlyInAnyOrder(
                "A AND E",
                "A AND F",
                "B AND C AND E",
                "B AND C AND F",
                "B AND D AND E",
                "B AND D AND F"
            )
        }

        "return in reasonable time for a complex AND expression".config(
            blockingTest = true,
            timeout = 150.milliseconds
        ) {
            val expression = listOf(
                "Apache-1.1",
                "Apache-2.0",
                "Artistic-1.0-Perl",
                "Artistic-2.0",
                "BSD-2-Clause",
                "BSD-2-Clause-Darwin",
                "BSD-2-Clause-Views",
                "BSD-3-Clause",
                "BSL-1.0",
                "CC-BY-2.5",
                "CC-BY-4.0",
                "CDDL-1.0",
                "CDDL-1.1",
                "CPL-1.0",
                "EPL-1.0",
                "EPL-2.0",
                "GPL-1.0-or-later",
                "GPL-2.0-only WITH Classpath-exception-2.0",
                "GPL-2.0-only",
                "GPL-2.0-or-later WITH Classpath-exception-2.0",
                "GPL-2.0-or-later",
                "ICU",
                "ISC",
                "JSON",
                "LGPL-2.0-only",
                "LGPL-2.0-or-later",
                "LGPL-2.1-only",
                "LGPL-2.1-or-later",
                "LGPL-3.0-only",
                "LPPL-1.3c",
                "MIT",
                "MPL-1.0",
                "MPL-1.1",
                "MPL-2.0",
                "NTP",
                "Noweb",
                "OFL-1.1",
                "Python-2.0",
                "Ruby",
                "Unicode-DFS-2016",
                "W3C",
                "W3C-19980720",
                "W3C-20150513"
            ).joinToString(separator = " AND ")

            val choices = expression.toSpdx().validChoices()

            choices.map { it.toString() } should containExactlyInAnyOrder(expression)
        }
    }
})
