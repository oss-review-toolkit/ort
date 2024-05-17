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

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.be
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.neverNullMatcher
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.ossreviewtoolkit.model.fromYaml
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.spdx.SpdxExpression.Strictness
import org.ossreviewtoolkit.utils.spdx.SpdxLicense.*
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseException.*

@Suppress("LargeClass")
class SpdxExpressionTest : WordSpec({
    "A dummy SpdxExpression" should {
        val dummyExpression = "a+ AND (b WITH exception1 OR c+) AND d WITH exception2"

        "be serializable to a string representation" {
            val spdxExpression = dummyExpression.toSpdx()

            val serializedExpression = spdxExpression.toYaml()

            serializedExpression shouldBe "--- \"$dummyExpression\"\n"
        }

        "be deserializable from a string representation" {
            val serializedExpression = "--- \"$dummyExpression\"\n"

            val deserializedExpression = serializedExpression.fromYaml<SpdxExpression>()

            deserializedExpression shouldBe SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxLicenseIdExpression("a", true),
                SpdxCompoundExpression(
                    SpdxOperator.OR,
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseIdExpression("b"),
                        "exception1"
                    ),
                    SpdxLicenseIdExpression("c", true)
                ),
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
                licenseRefWithLicenseRefException.toSpdx(Strictness.ALLOW_ANY) shouldBe
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseReferenceExpression("LicenseRef-ort-license"),
                        "LicenseRef-ort-exception"
                    )
                licenseRefWithLicenseRefException2.toSpdx(Strictness.ALLOW_ANY) shouldBe
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseReferenceExpression("LicenseRef-ort-license"),
                        "LicenseRef-ort-exception-2.0"
                    )
                licenseRefWithLicenseRef.toSpdx(Strictness.ALLOW_ANY) shouldBe
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseReferenceExpression("LicenseRef-ort-license"),
                        "LicenseRef-ort-license"
                    )
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
                licenseRefWithLicenseRefException.toSpdx(Strictness.ALLOW_LICENSEREF_EXCEPTIONS) shouldBe
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseReferenceExpression("LicenseRef-ort-license"),
                        "LicenseRef-ort-exception"
                    )
                licenseRefWithLicenseRefException2.toSpdx(Strictness.ALLOW_LICENSEREF_EXCEPTIONS) shouldBe
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseReferenceExpression("LicenseRef-ort-license"),
                        "LicenseRef-ort-exception-2.0"
                    )

                shouldThrow<SpdxException> {
                    licenseRefWithLicenseRef.toSpdx(Strictness.ALLOW_LICENSEREF_EXCEPTIONS)
                }
            }
        }
    }

    "parse()" should {
        "parse NONE correctly" {
            SpdxExpression.parse(SpdxConstants.NONE, Strictness.ALLOW_CURRENT).toString() shouldBe SpdxConstants.NONE
        }

        "parse NOASSERTION correctly" {
            SpdxExpression.parse(SpdxConstants.NOASSERTION, Strictness.ALLOW_CURRENT).toString() shouldBe
                SpdxConstants.NOASSERTION
        }

        "parse a license id correctly" {
            val actualExpression = "spdx.license-id".toSpdx()
            val expectedExpression = SpdxLicenseIdExpression("spdx.license-id")

            actualExpression shouldBe expectedExpression
        }

        "parse a license id starting with a digit correctly" {
            val actualExpression = "0license".toSpdx()
            val expectedExpression = SpdxLicenseIdExpression("0license")

            actualExpression shouldBe expectedExpression
        }

        "parse a license id with any later version correctly" {
            val actualExpression = "license+".toSpdx()
            val expectedExpression = SpdxLicenseIdExpression("license", orLaterVersion = true)

            actualExpression shouldBe expectedExpression
        }

        "parse a document ref correctly" {
            val actualExpression = "DocumentRef-document:LicenseRef-license".toSpdx()
            val expectedExpression = SpdxLicenseReferenceExpression("DocumentRef-document:LicenseRef-license")

            actualExpression shouldBe expectedExpression
        }

        "parse a license ref correctly" {
            val actualExpression = "LicenseRef-license".toSpdx()
            val expectedExpression = SpdxLicenseReferenceExpression("LicenseRef-license")

            actualExpression shouldBe expectedExpression
        }

        "parse a complex expression correctly" {
            val actualExpression = SpdxExpression.parse(
                "license1+ and ((license2 with exception1) OR license3+ AND license4 WITH exception2)"
            )
            val expectedExpression = SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxLicenseIdExpression("license1", orLaterVersion = true),
                SpdxCompoundExpression(
                    SpdxOperator.OR,
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseIdExpression("license2"),
                        "exception1"
                    ),
                    SpdxCompoundExpression(
                        SpdxOperator.AND,
                        SpdxLicenseIdExpression("license3", orLaterVersion = true),
                        SpdxLicenseWithExceptionExpression(
                            SpdxLicenseIdExpression("license4"),
                            "exception2"
                        )
                    )
                )
            )

            actualExpression shouldBe expectedExpression
        }

        "bind + stronger than WITH" {
            val actualExpression = "license+ WITH exception".toSpdx()
            val expectedExpression = SpdxLicenseWithExceptionExpression(
                SpdxLicenseIdExpression("license", orLaterVersion = true),
                "exception"
            )

            actualExpression shouldBe expectedExpression
        }

        "bind WITH stronger than AND" {
            val actualExpression = "license1 AND license2 WITH exception".toSpdx()
            val expectedExpression = SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxLicenseIdExpression("license1"),
                SpdxLicenseWithExceptionExpression(
                    SpdxLicenseIdExpression("license2"),
                    "exception"
                )
            )

            actualExpression shouldBe expectedExpression
        }

        "bind AND stronger than OR" {
            val actualExpression = "license1 OR license2 AND license3".toSpdx()
            val expectedExpression = SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxLicenseIdExpression("license1"),
                SpdxCompoundExpression(
                    SpdxOperator.AND,
                    SpdxLicenseIdExpression("license2"),
                    SpdxLicenseIdExpression("license3")
                )
            )

            actualExpression shouldBe expectedExpression
        }

        "respect parentheses for binding strength of operators" {
            val actualExpression = "(license1 OR license2) AND license3".toSpdx()
            val expectedExpression = SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxCompoundExpression(
                    SpdxOperator.OR,
                    SpdxLicenseIdExpression("license1"),
                    SpdxLicenseIdExpression("license2")
                ),
                SpdxLicenseIdExpression("license3")
            )

            actualExpression shouldBe expectedExpression
        }

        "fail if + is used in an exception expression" {
            val exception = shouldThrow<SpdxException> {
                "license WITH exception+".toSpdx()
            }

            exception.message shouldBe "Unexpected token 'PLUS(position=23)'."
        }

        "fail if a compound expression is used before WITH" {
            val exception = shouldThrow<SpdxException> {
                "(license1 AND license2) WITH exception".toSpdx()
            }

            exception.message shouldBe "Unexpected token 'WITH(position=25)'."
        }

        "fail on an invalid symbol" {
            val exception = shouldThrow<SpdxException> {
                "/".toSpdx()
            }

            exception.message shouldBe "Unexpected character '/' at position 1."
        }

        "fail on a syntax error" {
            val exception = shouldThrow<SpdxException> {
                "((".toSpdx()
            }

            exception.message shouldBe "Unexpected token 'null'."
        }

        "work for deprecated license identifiers" {
            assertSoftly {
                "eCos-2.0".toSpdx() shouldBe SpdxLicenseIdExpression("eCos-2.0")
                "Nunit".toSpdx() shouldBe SpdxLicenseIdExpression("Nunit")
                "StandardML-NJ".toSpdx() shouldBe SpdxLicenseIdExpression("StandardML-NJ")
                "wxWindows".toSpdx() shouldBe SpdxLicenseIdExpression("wxWindows")
            }
        }
    }

    "creating a compound expression" should {
        "fail if the expression has less than two children" {
            shouldThrow<IllegalArgumentException> {
                SpdxCompoundExpression(SpdxOperator.AND, emptyList())
            }

            shouldThrow<IllegalArgumentException> {
                SpdxCompoundExpression(SpdxOperator.AND, listOf(SpdxLicenseIdExpression("license")))
            }
        }
    }

    "normalize()" should {
        "normalize the case of SPDX licenses" {
            SpdxLicense.entries.filterNot { it.deprecated }.forEach {
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
            "GPL-2.0-or-later WITH Classpath-exception-2.0".decompose() should containExactly(
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

    "sort()" should {
        "not change already sorted expressions" {
            "a AND b".toSpdx().sorted() should beString("a AND b")
            "(a OR b) AND (c OR d)".toSpdx().sorted() should beString("(a OR b) AND (c OR d)")
        }

        "correctly sort simple expressions" {
            "b AND a".toSpdx().sorted() should beString("a AND b")
            "b OR a".toSpdx().sorted() should beString("a OR b")
            "c AND b AND a".toSpdx().sorted() should beString("a AND b AND c")
        }

        "correctly sort a complex expression" {
            "(h OR g) AND (f OR e) OR (c OR d) AND (a OR b)".toSpdx().sorted() should
                beString("((a OR b) AND (c OR d)) OR ((e OR f) AND (g OR h))")
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

        "be explicit about the choices even if they could be simplified" {
            val choices = "(a OR b) AND (a OR b)".toSpdx().validChoices()

            choices.map { it.toString() } should containExactlyInAnyOrder(
                "a AND a",
                "b AND a",
                "b AND b"
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

        "return true for a simplified choice for a complex expression" {
            val license = "(MIT OR GPL-2.0-only) AND (MIT OR BSD-3-Clause OR GPL-1.0-or-later) AND " +
                "(MIT OR BSD-3-Clause OR GPL-2.0-only)"

            license.toSpdx().isValidChoice("MIT".toSpdx()) shouldBe true
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
})

private fun beString(expected: String): Matcher<SpdxExpression> =
    neverNullMatcher { spdxExpression -> be(expected).test(spdxExpression.toString()) }
