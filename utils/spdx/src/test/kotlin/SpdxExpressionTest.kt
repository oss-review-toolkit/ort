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

                "Nunit".toSpdx().normalize() shouldBe ZLIB_ACKNOWLEDGEMENT.toExpression()
                "StandardML-NJ".toSpdx().normalize() shouldBe SMLNJ.toExpression()
                "bzip2-1.0.5".toSpdx().normalize() shouldBe BZIP2_1_0_6.toExpression()
                "eCos-2.0".toSpdx().normalize() shouldBe (GPL_2_0_OR_LATER with ECOS_EXCEPTION_2_0)
                "wxWindows".toSpdx().normalize() shouldBe (GPL_2_0_OR_LATER with WXWINDOWS_EXCEPTION_3_1)
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
