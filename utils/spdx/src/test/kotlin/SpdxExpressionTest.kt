/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.be
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.neverNullMatcher
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.ossreviewtoolkit.utils.spdx.SpdxExpression.Strictness
import org.ossreviewtoolkit.utils.spdx.SpdxLicense.*
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseException.*
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice

class SpdxExpressionTest : WordSpec() {
    private val yamlMapper = YAMLMapper()

    init {
        "toString()" should {
            "return the textual SPDX expression" {
                val expression = "a+ AND (b WITH exception1 OR c+) AND d WITH exception2"

                expression.toSpdx() should beString(expression)
            }

            "not include unnecessary parenthesis" {
                val expression = "(a AND (b AND c) AND (d OR (e WITH exception)))"

                expression.toSpdx() should beString("a AND b AND c AND (d OR e WITH exception)")
            }

            "always add parentheses around groups with different operators" {
                val expression1 = "a AND b AND c OR d AND e AND f"
                val expression2 = "(a OR b OR c) AND (d OR e OR f)"
                val expression3 = "(a OR b AND c) AND (d AND e OR f)"

                expression1.toSpdx() should beString("(a AND b AND c) OR (d AND e AND f)")
                expression2.toSpdx() should beString("(a OR b OR c) AND (d OR e OR f)")
                expression3.toSpdx() should beString("(a OR (b AND c)) AND ((d AND e) OR f)")
            }
        }

        "A dummy SpdxExpression" should {
            val dummyExpression = "a+ AND (b WITH exception1 OR c+) AND d WITH exception2"

            "be serializable to a string representation" {
                val spdxExpression = dummyExpression.toSpdx()

                val serializedExpression = yamlMapper.writeValueAsString(spdxExpression)

                serializedExpression shouldBe "--- \"$dummyExpression\"\n"
            }

            "be deserializable from a string representation" {
                val serializedExpression = "--- \"$dummyExpression\"\n"

                val deserializedExpression = yamlMapper.readValue<SpdxExpression>(serializedExpression)

                deserializedExpression shouldBe SpdxCompoundExpression(
                    SpdxCompoundExpression(
                        SpdxLicenseIdExpression("a", true),
                        SpdxOperator.AND,
                        SpdxCompoundExpression(
                            SpdxLicenseWithExceptionExpression(
                                SpdxLicenseIdExpression("b"),
                                "exception1"
                            ),
                            SpdxOperator.OR,
                            SpdxLicenseIdExpression("c", true)
                        )
                    ),
                    SpdxOperator.AND,
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseIdExpression("d"),
                        "exception2"
                    )
                )
            }

            "be valid in lenient mode" {
                dummyExpression.toSpdx(Strictness.ALLOW_ANY)
            }

            "be invalid in deprecated mode" {
                shouldThrow<SpdxException> {
                    dummyExpression.toSpdx(Strictness.ALLOW_DEPRECATED)
                }
            }

            "be invalid in strict mode" {
                shouldThrow<SpdxException> {
                    dummyExpression.toSpdx(Strictness.ALLOW_CURRENT)
                }
            }
        }

        "An SpdxExpression with deprecated identifiers" should {
            val deprecatedExpression = "GPL-1.0+"
            val deprecatedExpressionWithException = "GPL-2.0-with-classpath-exception"

            "be valid in lenient mode" {
                assertSoftly {
                    deprecatedExpression.toSpdx(Strictness.ALLOW_ANY)
                    deprecatedExpressionWithException.toSpdx(Strictness.ALLOW_ANY)
                }
            }

            "be valid in deprecated mode" {
                assertSoftly {
                    deprecatedExpression.toSpdx(Strictness.ALLOW_DEPRECATED)
                    deprecatedExpressionWithException.toSpdx(Strictness.ALLOW_DEPRECATED)
                }
            }

            "be invalid in strict mode" {
                assertSoftly {
                    shouldThrow<SpdxException> {
                        deprecatedExpression.toSpdx(Strictness.ALLOW_CURRENT)
                    }

                    shouldThrow<SpdxException> {
                        deprecatedExpressionWithException.toSpdx(Strictness.ALLOW_CURRENT)
                    }
                }
            }
        }

        "An SpdxExpression with current identifiers" should {
            val currentExpression = "GPL-1.0-only"
            val currentExpressionWithException = "GPL-2.0-or-later WITH Classpath-exception-2.0"

            "be valid in lenient mode" {
                assertSoftly {
                    currentExpression.toSpdx(Strictness.ALLOW_ANY)
                    currentExpressionWithException.toSpdx(Strictness.ALLOW_ANY)
                }
            }

            "be valid in deprecated mode" {
                assertSoftly {
                    currentExpression.toSpdx(Strictness.ALLOW_DEPRECATED)
                    currentExpressionWithException.toSpdx(Strictness.ALLOW_DEPRECATED)
                }
            }

            "be valid in strict mode" {
                assertSoftly {
                    currentExpression.toSpdx(Strictness.ALLOW_CURRENT)
                    currentExpressionWithException.toSpdx(Strictness.ALLOW_CURRENT)
                }
            }
        }

        "An SpdxExpression with a LicenseRef exception" should {
            val licenseRefWithLicenseRefException = "LicenseRef-ort-license WITH LicenseRef-ort-exception"
            val licenseRefWithLicenseRefException2 = "LicenseRef-ort-license WITH LicenseRef-ort-exception-2.0"
            val licenseRefWithLicenseRef = "LicenseRef-ort-license WITH LicenseRef-ort-license"

            "be valid in lenient mode" {
                assertSoftly {
                    licenseRefWithLicenseRefException.toSpdx(Strictness.ALLOW_ANY)
                    licenseRefWithLicenseRefException2.toSpdx(Strictness.ALLOW_ANY)
                    licenseRefWithLicenseRef.toSpdx(Strictness.ALLOW_ANY)
                }
            }

            "be invalid in deprecated mode" {
                assertSoftly {
                    shouldThrow<SpdxException> {
                        licenseRefWithLicenseRefException.toSpdx(Strictness.ALLOW_DEPRECATED)
                    }

                    shouldThrow<SpdxException> {
                        licenseRefWithLicenseRefException2.toSpdx(Strictness.ALLOW_DEPRECATED)
                    }

                    shouldThrow<SpdxException> {
                        licenseRefWithLicenseRef.toSpdx(Strictness.ALLOW_DEPRECATED)
                    }
                }
            }

            "be invalid in strict mode" {
                assertSoftly {
                    shouldThrow<SpdxException> {
                        licenseRefWithLicenseRefException.toSpdx(Strictness.ALLOW_CURRENT)
                    }

                    shouldThrow<SpdxException> {
                        licenseRefWithLicenseRefException2.toSpdx(Strictness.ALLOW_CURRENT)
                    }

                    shouldThrow<SpdxException> {
                        licenseRefWithLicenseRef.toSpdx(Strictness.ALLOW_CURRENT)
                    }
                }
            }

            "be valid when allowing LicenseRef exceptions" {
                assertSoftly {
                    licenseRefWithLicenseRefException.toSpdx(Strictness.ALLOW_LICENSEREF_EXCEPTIONS)
                    licenseRefWithLicenseRefException2.toSpdx(Strictness.ALLOW_LICENSEREF_EXCEPTIONS)

                    shouldThrow<SpdxException> {
                        licenseRefWithLicenseRef.toSpdx(Strictness.ALLOW_LICENSEREF_EXCEPTIONS)
                    }
                }
            }
        }

        "The expression parser" should {
            "work for deprecated license identifiers" {
                assertSoftly {
                    "eCos-2.0".toSpdx() shouldBe SpdxLicenseIdExpression("eCos-2.0")
                    "Nunit".toSpdx() shouldBe SpdxLicenseIdExpression("Nunit")
                    "StandardML-NJ".toSpdx() shouldBe SpdxLicenseIdExpression("StandardML-NJ")
                    "wxWindows".toSpdx() shouldBe SpdxLicenseIdExpression("wxWindows")
                }
            }

            "normalize the case of SPDX licenses" {
                SpdxLicense.values().filterNot { it.deprecated }.forEach {
                    it.id.lowercase().toSpdx().normalize() shouldBe it.toExpression()
                }
            }

            "normalize deprecated licenses to non-deprecated ones" {
                assertSoftly {
                    "AGPL-1.0".toSpdx().normalize() shouldBe AGPL_1_0_ONLY.toExpression()
                    "AGPL-1.0+".toSpdx().normalize() shouldBe SpdxLicenseIdExpression("AGPL-1.0-or-later", true)

                    "AGPL-3.0".toSpdx().normalize() shouldBe AGPL_3_0_ONLY.toExpression()
                    "AGPL-3.0+".toSpdx().normalize() shouldBe SpdxLicenseIdExpression("AGPL-3.0-or-later", true)

                    "GFDL-1.1".toSpdx().normalize() shouldBe GFDL_1_1_ONLY.toExpression()
                    "GFDL-1.1+".toSpdx().normalize() shouldBe SpdxLicenseIdExpression("GFDL-1.1-or-later", true)

                    "GFDL-1.2".toSpdx().normalize() shouldBe GFDL_1_2_ONLY.toExpression()
                    "GFDL-1.2+".toSpdx().normalize() shouldBe SpdxLicenseIdExpression("GFDL-1.2-or-later", true)

                    "GFDL-1.3".toSpdx().normalize() shouldBe GFDL_1_3_ONLY.toExpression()
                    "GFDL-1.3+".toSpdx().normalize() shouldBe SpdxLicenseIdExpression("GFDL-1.3-or-later", true)

                    "GPL-1.0".toSpdx().normalize() shouldBe GPL_1_0_ONLY.toExpression()
                    "GPL-1.0+".toSpdx().normalize() shouldBe SpdxLicenseIdExpression("GPL-1.0-or-later", true)

                    "GPL-2.0".toSpdx().normalize() shouldBe GPL_2_0_ONLY.toExpression()
                    "GPL-2.0+".toSpdx().normalize() shouldBe SpdxLicenseIdExpression("GPL-2.0-or-later", true)

                    "GPL-3.0".toSpdx().normalize() shouldBe GPL_3_0_ONLY.toExpression()
                    "GPL-3.0+".toSpdx().normalize() shouldBe SpdxLicenseIdExpression("GPL-3.0-or-later", true)

                    "LGPL-2.0".toSpdx().normalize() shouldBe LGPL_2_0_ONLY.toExpression()
                    "LGPL-2.0+".toSpdx().normalize() shouldBe SpdxLicenseIdExpression("LGPL-2.0-or-later", true)

                    "LGPL-2.1".toSpdx().normalize() shouldBe LGPL_2_1_ONLY.toExpression()
                    "LGPL-2.1+".toSpdx().normalize() shouldBe SpdxLicenseIdExpression("LGPL-2.1-or-later", true)

                    "LGPL-3.0".toSpdx().normalize() shouldBe LGPL_3_0_ONLY.toExpression()
                    "LGPL-3.0+".toSpdx().normalize() shouldBe SpdxLicenseIdExpression("LGPL-3.0-or-later", true)

                    // These have no known successors, so just keep them.
                    "eCos-2.0".toSpdx().normalize() shouldBe ECOS_2_0.toExpression()
                    "Nunit".toSpdx().normalize() shouldBe NUNIT.toExpression()
                    "StandardML-NJ".toSpdx().normalize() shouldBe STANDARDML_NJ.toExpression()
                    "wxWindows".toSpdx().normalize() shouldBe WXWINDOWS.toExpression()
                }
            }

            "normalize deprecated license exceptions to non-deprecated ones" {
                assertSoftly {
                    "GPL-2.0-with-autoconf-exception".toSpdx().normalize() shouldBe
                            (GPL_2_0_ONLY with AUTOCONF_EXCEPTION_2_0)
                    "GPL-2.0-with-bison-exception".toSpdx().normalize() shouldBe
                            (GPL_2_0_ONLY with BISON_EXCEPTION_2_2)
                    "GPL-2.0-with-classpath-exception".toSpdx().normalize() shouldBe
                            (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0)
                    "GPL-2.0-with-font-exception".toSpdx().normalize() shouldBe
                            (GPL_2_0_ONLY with FONT_EXCEPTION_2_0)
                    "GPL-2.0-with-GCC-exception".toSpdx().normalize() shouldBe
                            (GPL_2_0_ONLY with GCC_EXCEPTION_2_0)
                    "GPL-3.0-with-autoconf-exception".toSpdx().normalize() shouldBe
                            (GPL_3_0_ONLY with AUTOCONF_EXCEPTION_3_0)
                    "GPL-3.0-with-GCC-exception".toSpdx().normalize() shouldBe
                            (GPL_3_0_ONLY with GCC_EXCEPTION_3_1)
                }
            }
        }

        "decompose()" should {
            fun String.decompose() = toSpdx().decompose().map { it.toString() }

            "not split-up compound expressions with a WITH operator" {
                "GPL-2.0-or-later WITH Classpath-exception-2.0".decompose() should containExactlyInAnyOrder(
                    "GPL-2.0-or-later WITH Classpath-exception-2.0"
                )
            }

            "split-up compound expressions with AND or OR operator but not ones with WITH operator" {
                "GPL-2.0-or-later WITH Classpath-exception-2.0 AND MIT".decompose() should containExactlyInAnyOrder(
                    "GPL-2.0-or-later WITH Classpath-exception-2.0",
                    "MIT"
                )
            }

            "work with LicenseRef-* identifiers" {
                "LicenseRef-gpl-2.0-custom WITH Classpath-exception-2.0 AND LicenseRef-scancode-commercial-license"
                    .decompose() should containExactlyInAnyOrder(
                    "LicenseRef-gpl-2.0-custom WITH Classpath-exception-2.0",
                    "LicenseRef-scancode-commercial-license"
                )
            }

            "return distinct strings" {
                "GPL-2.0-or-later WITH Classpath-exception-2.0 AND MIT AND MIT"
                    .decompose().count { it == "MIT" } shouldBe 1
            }

            "not merge license-exception pairs with single matching licenses" {
                "GPL-2.0-or-later WITH Classpath-exception-2.0 AND GPL-2.0-or-later"
                    .decompose() should containExactlyInAnyOrder(
                    "GPL-2.0-or-later WITH Classpath-exception-2.0",
                    "GPL-2.0-or-later"
                )
            }
        }

        "disjunctiveNormalForm()" should {
            "not change an expression already in DNF" {
                "a AND b OR c AND d".toSpdx().disjunctiveNormalForm() should beString("(a AND b) OR (c AND d)")
            }

            "correctly convert an OR on the left side of an AND expression" {
                "(a OR b) AND c".toSpdx().disjunctiveNormalForm() should beString("(a AND c) OR (b AND c)")
            }

            "correctly convert an OR on the right side of an AND expression" {
                "a AND (b OR c)".toSpdx().disjunctiveNormalForm() should beString("(a AND b) OR (a AND c)")
            }

            "correctly convert ORs on both sides of an AND expression" {
                "(a OR b) AND (c OR d)".toSpdx().disjunctiveNormalForm() should
                        beString("(a AND c) OR (a AND d) OR (b AND c) OR (b AND d)")
            }

            "correctly convert a complex expression" {
                "(a OR b) AND c AND (d OR e)".toSpdx().disjunctiveNormalForm() should
                        beString("(a AND c AND d) OR (a AND c AND e) OR (b AND c AND d) OR (b AND c AND e)")
            }
        }

        "sort()" should {
            "not change already sorted expressions" {
                "a AND b".toSpdx().sort() should beString("a AND b")
                "(a OR b) AND (c OR d)".toSpdx().sort() should beString("(a OR b) AND (c OR d)")
            }

            "correctly sort simple expressions" {
                "b AND a".toSpdx().sort() should beString("a AND b")
                "b OR a".toSpdx().sort() should beString("a OR b")
                "c AND b AND a".toSpdx().sort() should beString("a AND b AND c")
            }

            "correctly sort a complex expression" {
                "(h OR g) AND (f OR e) OR (c OR d) AND (a OR b)".toSpdx().sort() should
                        beString("((a OR b) AND (c OR d)) OR ((e OR f) AND (g OR h))")
            }
        }

        "validChoices()" should {
            "list the valid choices for a complex expression" {
                "(a OR b) AND c AND (d OR e)".toSpdx().validChoices() should containExactlyInAnyOrder(
                    "a AND c AND d".toSpdx(),
                    "a AND c AND e".toSpdx(),
                    "b AND c AND d".toSpdx(),
                    "b AND c AND e".toSpdx()
                )
            }

            "not contain a duplicate valid choice for a simple expression" {
                "a AND a".toSpdx().validChoices() should containExactlyInAnyOrder("a".toSpdx())
            }

            "not contain duplicate valid choice for a complex expression" {
                "(a OR b) AND (a OR b)".toSpdx().validChoices() should containExactlyInAnyOrder(
                    "a".toSpdx(),
                    "b".toSpdx(),
                    "a AND b".toSpdx()
                )
            }

            "not contain duplicate valid choice different left and right expressions" {
                "a AND a AND b".toSpdx().validChoices() should containExactlyInAnyOrder(
                    "a AND b".toSpdx()
                )
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
        }

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

            "apply the choice if the expression is not in DNF" {
                val expression = "(a OR b) AND c".toSpdx()
                val choice = "a AND c".toSpdx()

                val result = expression.applyChoice(choice)

                result shouldBe "a AND c".toSpdx()
            }

            "return the reduced subExpression in DNF if the choice was valid" {
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

            "throw an exception if the subExpression does not match and needs to be converted to a DNF" {
                val expression = "(a OR b) AND c AND (d OR e)".toSpdx()
                val choice = "a AND c AND d".toSpdx()
                val subExpression = "(a AND c AND d) OR (x AND y AND z)".toSpdx()

                shouldThrow<InvalidSubExpressionException> { expression.applyChoice(choice, subExpression) }
            }

            "apply the choice when the subExpression matches only a part of the expression" {
                val expression = "(a OR b) AND c".toSpdx()
                val choice = "a".toSpdx()
                val subExpression = "a OR b".toSpdx()

                val result = expression.applyChoice(choice, subExpression)

                result shouldBe "a AND c".toSpdx()
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
        }

        "equals()" should {
            "return true for semantically equal expressions" {
                "a".toSpdx() shouldBe "a".toSpdx()
                "a".toSpdx() shouldBe "a AND a".toSpdx()
                "a AND a".toSpdx() shouldBe "a".toSpdx()
                "a".toSpdx() shouldBe "a OR a".toSpdx()
                "a OR a".toSpdx() shouldBe "a".toSpdx()
                "a+".toSpdx() shouldBe "a+".toSpdx()
                "a WITH b".toSpdx() shouldBe "a WITH b".toSpdx()
                "a AND b".toSpdx() shouldBe "b AND a".toSpdx()
                "a AND (b OR c)".toSpdx() shouldBe "(a AND b) OR (a AND c)".toSpdx()
            }

            "return false for semantically different expressions" {
                "a".toSpdx() shouldNotBe "b".toSpdx()
                "a AND b".toSpdx() shouldNotBe "a OR b".toSpdx()
                "a OR b".toSpdx() shouldNotBe "a AND b".toSpdx()
                "a".toSpdx() shouldNotBe "a WITH b".toSpdx()
                "a".toSpdx() shouldNotBe "a OR b".toSpdx()
                "a".toSpdx() shouldNotBe "a AND b".toSpdx()
                "a".toSpdx() shouldNotBe "a+".toSpdx()
            }
        }

        "hashCode()" should {
            "return the same hashcode for equal expressions" {
                "a".toSpdx().hashCode() shouldBe "a".toSpdx().hashCode()
                "a".toSpdx().hashCode() shouldBe "a AND a".toSpdx().hashCode()
                "a".toSpdx().hashCode() shouldBe "a AND a AND a".toSpdx().hashCode()
                "a AND b".toSpdx().hashCode() shouldBe "b AND a".toSpdx().hashCode()
                "a OR b".toSpdx().hashCode() shouldBe "b OR a".toSpdx().hashCode()
                "a AND (b OR c)".toSpdx().hashCode() shouldBe "(a AND b) OR (a AND c)".toSpdx().hashCode()
            }
        }
    }
}

private fun beString(expected: String): Matcher<SpdxExpression> =
    neverNullMatcher { spdxExpression -> be(expected).test(spdxExpression.toString()) }
