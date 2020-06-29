/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.spdx

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.ossreviewtoolkit.spdx.SpdxExpression.Strictness
import org.ossreviewtoolkit.spdx.SpdxLicense.*
import org.ossreviewtoolkit.spdx.SpdxLicenseException.*

class SpdxExpressionTest : WordSpec() {
    private val yamlMapper = YAMLMapper()

    init {
        "toString()" should {
            "return the textual SPDX expression" {
                val expression = "license1+ AND (license2 WITH exception1 OR license3+) AND license4 WITH exception2"
                val spdxExpression = expression.toSpdx()

                val spdxString = spdxExpression.toString()

                spdxString shouldBe expression
            }

            "not include unnecessary parenthesis" {
                val spdxExpression =
                    "(license1 AND (license2 AND license3) AND (license4 OR (license5 WITH exception)))".toSpdx()

                val spdxString = spdxExpression.toString()

                spdxString shouldBe "license1 AND license2 AND license3 AND (license4 OR license5 WITH exception)"
            }
        }

        "A dummy SpdxExpression" should {
            val dummyExpression = "license1+ AND (license2 WITH exception1 OR license3+) AND license4 WITH exception2"

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
                        SpdxLicenseIdExpression("license1", true),
                        SpdxOperator.AND,
                        SpdxCompoundExpression(
                            SpdxLicenseWithExceptionExpression(
                                SpdxLicenseIdExpression("license2"),
                                "exception1"
                            ),
                            SpdxOperator.OR,
                            SpdxLicenseIdExpression("license3", true)
                        )
                    ),
                    SpdxOperator.AND,
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseIdExpression("license4"),
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
                    it.id.toLowerCase().toSpdx().normalize() shouldBe it.toExpression()
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
                val spdxExpression = "(a AND b) OR (c AND d)".toSpdx()

                spdxExpression.disjunctiveNormalForm() shouldBe spdxExpression
            }

            "correctly convert an OR on the left side of an AND expression" {
                "(a OR b) AND c".toSpdx().disjunctiveNormalForm() shouldBe "(a AND c) OR (b AND c)".toSpdx()
            }

            "correctly convert an OR on the right side of an AND expression" {
                "a AND (b OR c)".toSpdx().disjunctiveNormalForm() shouldBe "(a AND b) OR (a AND c)".toSpdx()
            }

            "correctly convert ORs on both sides of an AND expression" {
                "(a OR b) AND (c OR d)".toSpdx().disjunctiveNormalForm() shouldBe
                        "(a AND c) OR (a AND d) OR (b AND c) OR (b AND d)".toSpdx()
            }

            "correctly convert a complex expression" {
                "(a OR b) AND c AND (d OR e)".toSpdx().disjunctiveNormalForm() shouldBe
                        "(a AND c AND d) OR (a AND c AND e) OR (b AND c AND d) OR (b AND c AND e)".toSpdx()
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
