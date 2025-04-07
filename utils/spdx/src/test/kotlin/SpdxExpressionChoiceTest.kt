/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.spdx

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.milliseconds

class SpdxExpressionChoiceTest : WordSpec({
    "applyChoice()" should {
        "return the choice for a simple expression" {
            val expression = "a".toSpdx()
            val choice = "a".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "a".toSpdx()
        }

        "throw an exception if the user chose a wrong license for a simple expression" {
            val expression = "a".toSpdx()
            val choice = "b".toSpdx()

            shouldThrow<InvalidLicenseChoiceException> { expression.applyChoice(choice) }
        }

        "return the new expression if only a part of the expression is matched by the subExpression" {
            val expression = "a OR b OR c".toSpdx()
            val choice = "b".toSpdx()
            val subExpression = "a OR b".toSpdx()

            val result = expression.applyChoice(choice, subExpression)

            result shouldBe "b OR c".toSpdx()
        }

        "work with choices that itself are a choice" {
            val expression = "a OR b OR c OR d".toSpdx()
            val choice = "a OR b".toSpdx()
            val subExpression = "a OR b OR c".toSpdx()

            val result = expression.applyChoice(choice, subExpression)

            result shouldBe "a OR b OR d".toSpdx()
        }

        "apply the choice if the expression contains multiple choices" {
            val expression = "a OR b OR c".toSpdx()
            val choice = "b".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "b".toSpdx()
        }

        "throw an exception if the chosen license is not a valid option" {
            val expression = "a OR b".toSpdx()
            val choice = "c".toSpdx()

            shouldThrow<InvalidLicenseChoiceException> { expression.applyChoice(choice) }
        }

        "apply the choice even if not literally contained in the expression" {
            val expression = "(a OR b) AND c".toSpdx()
            val choice = "a AND c".toSpdx()

            val result = expression.applyChoice(choice)

            result shouldBe "a AND c".toSpdx()
        }

        "return the reduced subExpression if the choice was valid" {
            val expression = "(a OR b) AND c AND (d OR e)".toSpdx()
            val choice = "a AND c AND d".toSpdx()
            val subExpression = "a AND c AND d OR a AND c AND e".toSpdx()

            val result = expression.applyChoice(choice, subExpression)

            result shouldBe "a AND c AND d OR b AND c AND d OR b AND c AND e".toSpdx()
        }

        "throw an exception if the subExpression does not match the simple expression" {
            val expression = "a".toSpdx()
            val choice = "x".toSpdx()
            val subExpression = "x OR y".toSpdx()

            shouldThrow<InvalidSubExpressionException> { expression.applyChoice(choice, subExpression) }
        }

        "throw an exception if the subExpression does not match the expression" {
            val expression = "a OR b OR c".toSpdx()
            val choice = "x".toSpdx()
            val subExpression = "x OR y OR z".toSpdx()

            shouldThrow<InvalidSubExpressionException> { expression.applyChoice(choice, subExpression) }
        }

        "throw an exception if the subExpression does not match" {
            val expression = "(a OR b) AND c AND (d OR e)".toSpdx()
            val choice = "a AND c AND d".toSpdx()
            val subExpression = "(a AND c AND d) OR (x AND y AND z)".toSpdx()

            shouldThrow<InvalidSubExpressionException> { expression.applyChoice(choice, subExpression) }
        }
    }

    "applyChoices()" should {
        "return the correct result if a single choice is applied" {
            val expression = "a OR b OR c OR d".toSpdx()

            val choices = listOf(SpdxLicenseChoice(expression, "a".toSpdx()))

            val result = expression.applyChoices(choices)

            result shouldBe "a".toSpdx()
        }

        "return the correct result if multiple simple choices are applied" {
            val expression = "(a OR b) AND (c OR d)".toSpdx()

            val choices = listOf(
                SpdxLicenseChoice("a OR b".toSpdx(), "a".toSpdx()),
                SpdxLicenseChoice("c OR d".toSpdx(), "c".toSpdx())
            )

            val result = expression.applyChoices(choices)

            result shouldBe "a AND c".toSpdx()
        }

        "ignore invalid sub-expressions and return the correct result for valid choices" {
            val expression = "a OR b OR c OR d".toSpdx()

            val choices = listOf(
                SpdxLicenseChoice("a OR b".toSpdx(), "b".toSpdx()), // b OR c OR d
                SpdxLicenseChoice("a OR c".toSpdx(), "a".toSpdx()) // not applied
            )

            val result = expression.applyChoices(choices)

            result shouldBe "b OR c OR d".toSpdx()
        }

        "apply the second choice to the effective license after the first choice" {
            val expression = "a OR b OR c OR d".toSpdx()

            val choices = listOf(
                SpdxLicenseChoice("a OR b".toSpdx(), "b".toSpdx()), // b OR c OR d
                SpdxLicenseChoice("b OR c".toSpdx(), "b".toSpdx()) // b OR d
            )

            val result = expression.applyChoices(choices)

            result shouldBe "b OR d".toSpdx()
        }

        "apply a single choice to multiple expressions" {
            val expression = "(a OR b) AND (c OR d) AND (a OR e)".toSpdx()

            val choices = listOf(
                SpdxLicenseChoice("a OR b".toSpdx(), "a".toSpdx()),
                SpdxLicenseChoice("a OR e".toSpdx(), "a".toSpdx())
            )

            val result = expression.applyChoices(choices)

            result shouldBe "a AND (c OR d) AND a".toSpdx()
        }

        "given expressions should match semantically equivalent license expressions" {
            val expression = "a OR b".toSpdx()

            val choices = listOf(
                SpdxLicenseChoice("b OR a".toSpdx(), "a".toSpdx())
            )

            val result = expression.applyChoices(choices)

            result shouldBe "a".toSpdx()
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
            val spdxExpression = "(a OR b) AND c AND (d OR e)".toSpdx()

            spdxExpression.isValidChoice("a AND c AND d".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("a AND d AND c".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("c AND a AND d".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("c AND d AND a".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("d AND a AND c".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("d AND c AND a".toSpdx()) shouldBe true

            spdxExpression.isValidChoice("a AND c AND e".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("b AND c AND d".toSpdx()) shouldBe true
            spdxExpression.isValidChoice("b AND c AND e".toSpdx()) shouldBe true
        }

        "return false if a choice is invalid" {
            val spdxExpression = "(a OR b) AND c AND (d OR e)".toSpdx()

            spdxExpression.isValidChoice("a".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("a AND b".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("a AND c".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("a AND d".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("a AND e".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("a AND b AND c".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("a AND b AND d".toSpdx()) shouldBe false
            spdxExpression.isValidChoice("a AND b AND c AND d".toSpdx()) shouldBe false
        }

        "return true for a simplified choice for a complex expression" {
            val license = "(MIT OR GPL-2.0-only) AND (MIT OR BSD-3-Clause OR GPL-1.0-or-later) AND " +
                "(MIT OR BSD-3-Clause OR GPL-2.0-only)"

            license.toSpdx().isValidChoice("MIT".toSpdx()) shouldBe true
        }
    }

    "offersChoice()" should {
        "return true if the expression contains the OR operator" {
            "a OR b".toSpdx().offersChoice() shouldBe true
            "a AND b OR c".toSpdx().offersChoice() shouldBe true
            "a OR b AND c".toSpdx().offersChoice() shouldBe true
            "a AND b AND c OR d".toSpdx().offersChoice() shouldBe true
        }

        "return false if the expression does not contain the OR operator" {
            "a".toSpdx().offersChoice() shouldBe false
            "a AND b".toSpdx().offersChoice() shouldBe false
            "a AND b AND c".toSpdx().offersChoice() shouldBe false
        }

        "return false if the expression contains the OR operator with equal operands" {
            "a OR a".toSpdx().offersChoice() shouldBe false
        }
    }

    "validChoices()" should {
        "return the original terms of an expression in DNF" {
            val choices = "(a AND b) OR (c AND d) OR (e AND f)".toSpdx().validChoices()

            choices.map { it.toString() } should containExactlyInAnyOrder(
                "a AND b",
                "c AND d",
                "e AND f"
            )
        }

        "return the distribution of all terms of an expression in CNF" {
            val choices = "(a OR b) AND (c OR d)".toSpdx().validChoices()

            choices.map { it.toString() } should containExactlyInAnyOrder(
                "a AND c",
                "a AND d",
                "b AND c",
                "b AND d"
            )
        }

        "return the valid choices for a complex expression" {
            val choices = "(a OR b) AND c AND (d OR e)".toSpdx().validChoices()

            choices.map { it.toString() } should containExactlyInAnyOrder(
                "a AND c AND d",
                "a AND c AND e",
                "b AND c AND d",
                "b AND c AND e"
            )
        }

        "return the valid choices for a nested expression" {
            val choices = "(a OR (b AND (c OR d))) AND (e OR f)".toSpdx().validChoices()

            choices.map { it.toString() } should containExactlyInAnyOrder(
                "a AND e",
                "a AND f",
                "b AND c AND e",
                "b AND c AND f",
                "b AND d AND e",
                "b AND d AND f"
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
