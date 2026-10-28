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
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.SpdxLicense.*
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseException.*
import org.ossreviewtoolkit.utils.spdxexpression.SpdxExpression.Strictness

class SpdxExpressionTest : WordSpec({
    "A dummy SpdxExpression" should {
        val dummyExpression = "A+ AND (B WITH E OR C+) AND D WITH F"

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
                SpdxLicenseIdExpression("A", true),
                SpdxCompoundExpression(
                    SpdxOperator.OR,
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseIdExpression("B"),
                        "E"
                    ),
                    SpdxLicenseIdExpression("C", true)
                ),
                SpdxLicenseWithExceptionExpression(
                    SpdxLicenseIdExpression("D"),
                    "F"
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
        val licenseRefWithLicenseRefException = "LicenseRef-A WITH LicenseRef-B-exception"
        val licenseRefWithLicenseRefException2 = "LicenseRef-A WITH LicenseRef-C-exception-2.0"
        val licenseRefWithLicenseRef = "LicenseRef-A WITH LicenseRef-A"

        "be valid in lenient mode" {
            assertSoftly {
                licenseRefWithLicenseRefException.toSpdx(Strictness.ALLOW_ANY) shouldBe
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseReferenceExpression("LicenseRef-A"),
                        "LicenseRef-B-exception"
                    )
                licenseRefWithLicenseRefException2.toSpdx(Strictness.ALLOW_ANY) shouldBe
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseReferenceExpression("LicenseRef-A"),
                        "LicenseRef-C-exception-2.0"
                    )
                licenseRefWithLicenseRef.toSpdx(Strictness.ALLOW_ANY) shouldBe
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseReferenceExpression("LicenseRef-A"),
                        "LicenseRef-A"
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
                        SpdxLicenseReferenceExpression("LicenseRef-A"),
                        "LicenseRef-B-exception"
                    )
                licenseRefWithLicenseRefException2.toSpdx(Strictness.ALLOW_LICENSEREF_EXCEPTIONS) shouldBe
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseReferenceExpression("LicenseRef-A"),
                        "LicenseRef-C-exception-2.0"
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
            val actualExpression = "A".toSpdx()
            val expectedExpression = SpdxLicenseIdExpression("A")

            actualExpression shouldBe expectedExpression
        }

        "parse a license id starting with a digit correctly" {
            val actualExpression = "0A".toSpdx()
            val expectedExpression = SpdxLicenseIdExpression("0A")

            actualExpression shouldBe expectedExpression
        }

        "parse a license id with any later version correctly" {
            val actualExpression = "A+".toSpdx()
            val expectedExpression = SpdxLicenseIdExpression("A", orLaterVersion = true)

            actualExpression shouldBe expectedExpression
        }

        "parse a document ref correctly" {
            val actualExpression = "DocumentRef-A:LicenseRef-B".toSpdx()
            val expectedExpression = SpdxLicenseReferenceExpression("DocumentRef-A:LicenseRef-B")

            actualExpression shouldBe expectedExpression
        }

        "parse a license ref correctly" {
            val actualExpression = "LicenseRef-A".toSpdx()
            val expectedExpression = SpdxLicenseReferenceExpression("LicenseRef-A")

            actualExpression shouldBe expectedExpression
        }

        "parse a complex expression correctly" {
            val actualExpression = SpdxExpression.parse(
                "A+ and ((B with E) OR C+ AND D WITH F)"
            )
            val expectedExpression = SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxLicenseIdExpression("A", orLaterVersion = true),
                SpdxCompoundExpression(
                    SpdxOperator.OR,
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseIdExpression("B"),
                        "E"
                    ),
                    SpdxCompoundExpression(
                        SpdxOperator.AND,
                        SpdxLicenseIdExpression("C", orLaterVersion = true),
                        SpdxLicenseWithExceptionExpression(
                            SpdxLicenseIdExpression("D"),
                            "F"
                        )
                    )
                )
            )

            actualExpression shouldBe expectedExpression
        }

        "bind + stronger than WITH" {
            val actualExpression = "A+ WITH E".toSpdx()
            val expectedExpression = SpdxLicenseWithExceptionExpression(
                SpdxLicenseIdExpression("A", orLaterVersion = true),
                "E"
            )

            actualExpression shouldBe expectedExpression
        }

        "bind WITH stronger than AND" {
            val actualExpression = "A AND B WITH E".toSpdx()
            val expectedExpression = SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxLicenseIdExpression("A"),
                SpdxLicenseWithExceptionExpression(
                    SpdxLicenseIdExpression("B"),
                    "E"
                )
            )

            actualExpression shouldBe expectedExpression
        }

        "bind AND stronger than OR" {
            val actualExpression = "A OR B AND C".toSpdx()
            val expectedExpression = SpdxCompoundExpression(
                SpdxOperator.OR,
                SpdxLicenseIdExpression("A"),
                SpdxCompoundExpression(
                    SpdxOperator.AND,
                    SpdxLicenseIdExpression("B"),
                    SpdxLicenseIdExpression("C")
                )
            )

            actualExpression shouldBe expectedExpression
        }

        "respect parentheses for binding strength of operators" {
            val actualExpression = "(A OR B) AND C".toSpdx()
            val expectedExpression = SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxCompoundExpression(
                    SpdxOperator.OR,
                    SpdxLicenseIdExpression("A"),
                    SpdxLicenseIdExpression("B")
                ),
                SpdxLicenseIdExpression("C")
            )

            actualExpression shouldBe expectedExpression
        }

        "fail if + is used in an exception expression" {
            val exception = shouldThrow<SpdxException> {
                "A WITH E+".toSpdx()
            }

            exception.message shouldBe "Unexpected token 'PLUS(position=9)'."
        }

        "fail if a compound expression is used before WITH" {
            val exception = shouldThrow<SpdxException> {
                "(A AND B) WITH E".toSpdx()
            }

            exception.message shouldBe "Unexpected token 'WITH(position=11)'."
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
            "A WITH E".decompose() should containExactly("A WITH E")
        }

        "split-up compound expressions with AND or OR operator but not ones with WITH operator" {
            "A WITH E AND B".decompose() should containExactlyInAnyOrder("A WITH E", "B")
        }

        "work with LicenseRef-* identifiers" {
            "LicenseRef-A WITH E AND LicenseRef-B".decompose() should containExactlyInAnyOrder(
                "LicenseRef-A WITH E",
                "LicenseRef-B"
            )
        }

        "return distinct strings" {
            "A WITH E AND B AND B".decompose().count { it == "B" } shouldBe 1
        }

        "not merge license-exception pairs with single matching licenses" {
            "A WITH E AND A"
                .decompose() should containExactlyInAnyOrder(
                "A WITH E",
                "A"
            )
        }
    }

    "sort()" should {
        "not change already sorted expressions" {
            "A AND B".toSpdx().sorted() should beString("A AND B")
            "(A OR B) AND (C OR D)".toSpdx().sorted() should beString("(A OR B) AND (C OR D)")
        }

        "correctly sort simple expressions" {
            "B AND A".toSpdx().sorted() should beString("A AND B")
            "B OR A".toSpdx().sorted() should beString("A OR B")
            "C AND B AND A".toSpdx().sorted() should beString("A AND B AND C")
        }

        "correctly sort a complex expression" {
            "(H OR G) AND (F OR E) OR (C OR D) AND (A OR B)".toSpdx().sorted() should
                beString("((A OR B) AND (C OR D)) OR ((E OR F) AND (G OR H))")
        }

        "correctly sort sub-expressions with the same prefixes" {
            "(A OR D) AND (A OR B)".toSpdx().sorted() should beString("(A OR B) AND (A OR D)")
            "(A AND B AND D) OR (A AND B AND C)".toSpdx().sorted() should beString("(A AND B AND C) OR (A AND B AND D)")
        }

        "correctly sort nested compound expressions" {
            SpdxCompoundExpression(
                SpdxOperator.AND,
                SpdxCompoundExpression(SpdxOperator.AND, "GPL-3.0-or-later".toSpdx(), "bzip2-1.0.6".toSpdx()),
                SpdxCompoundExpression(
                    SpdxOperator.AND,
                    SpdxCompoundExpression(SpdxOperator.AND, "MS-PL".toSpdx(), "Apache-2.0".toSpdx()),
                    SpdxCompoundExpression(SpdxOperator.AND, "Zlib".toSpdx(), "BSD-3-Clause".toSpdx())
                )
            ).sorted() should
                beString("Apache-2.0 AND BSD-3-Clause AND bzip2-1.0.6 AND GPL-3.0-or-later AND MS-PL AND Zlib")
        }
    }

    "equals()" should {
        "return true for semantically equal expressions" {
            "A".toSpdx() shouldBe "A".toSpdx()
            "A".toSpdx() shouldBe "A AND A".toSpdx()
            "A AND A".toSpdx() shouldBe "A".toSpdx()
            "A".toSpdx() shouldBe "A OR A".toSpdx()
            "A OR A".toSpdx() shouldBe "A".toSpdx()
            "A+".toSpdx() shouldBe "A+".toSpdx()
            "A WITH B".toSpdx() shouldBe "A WITH B".toSpdx()
            "A AND B".toSpdx() shouldBe "B AND A".toSpdx()
            "A AND (B OR C)".toSpdx() shouldBe "(A AND B) OR (A AND C)".toSpdx()
        }

        "return false for semantically different expressions" {
            "A".toSpdx() shouldNotBe "B".toSpdx()
            "A AND B".toSpdx() shouldNotBe "A OR B".toSpdx()
            "A OR B".toSpdx() shouldNotBe "A AND B".toSpdx()
            "A".toSpdx() shouldNotBe "A WITH B".toSpdx()
            "A".toSpdx() shouldNotBe "A OR B".toSpdx()
            "A".toSpdx() shouldNotBe "A AND B".toSpdx()
            "A".toSpdx() shouldNotBe "A+".toSpdx()
        }

        "compare expressions case-insensitively" {
            "CDDL-1.1 OR GPL-2.0-only WITH Classpath-Exception-2.0".toSpdx() shouldBe
                "Cddl-1.1 OR Gpl-2.0-only WITH Classpath-exception-2.0".toSpdx()
        }
    }

    "hashCode()" should {
        "return the same hashcode for equal expressions" {
            "A".toSpdx().hashCode() shouldBe "A".toSpdx().hashCode()
            "A".toSpdx().hashCode() shouldBe "A AND A".toSpdx().hashCode()
            "A".toSpdx().hashCode() shouldBe "A AND A AND A".toSpdx().hashCode()
            "A AND B".toSpdx().hashCode() shouldBe "B AND A".toSpdx().hashCode()
            "A OR B".toSpdx().hashCode() shouldBe "B OR A".toSpdx().hashCode()
            "A AND (B OR C)".toSpdx().hashCode() shouldBe "(A AND B) OR (A AND C)".toSpdx().hashCode()
        }
    }

    "toString()" should {
        "return the textual SPDX expression" {
            val expression = "A+ AND (B WITH E OR C+) AND D WITH F"

            expression.toSpdx() should beString(expression)
        }

        "not include unnecessary parenthesis" {
            val expression = "(A AND (B AND C) AND (D OR (F WITH E)))"

            expression.toSpdx() should beString("A AND B AND C AND (D OR F WITH E)")
        }

        "always add parentheses around groups with different operators" {
            val expression1 = "A AND B AND C OR D AND E AND F"
            val expression2 = "(A OR B OR C) AND (D OR E OR F)"
            val expression3 = "(A OR B AND C) AND (D AND E OR F)"

            expression1.toSpdx() should beString("(A AND B AND C) OR (D AND E AND F)")
            expression2.toSpdx() should beString("(A OR B OR C) AND (D OR E OR F)")
            expression3.toSpdx() should beString("(A OR (B AND C)) AND ((D AND E) OR F)")
        }
    }
})

private fun beString(expected: String): Matcher<SpdxExpression> =
    neverNullMatcher { spdxExpression -> be(expected).test(spdxExpression.toString()) }
