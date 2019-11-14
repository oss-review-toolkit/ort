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

package com.here.ort.spdx

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue

import com.here.ort.spdx.SpdxExpression.Strictness
import com.here.ort.spdx.SpdxLicense.*
import com.here.ort.spdx.SpdxLicenseException.*

import io.kotlintest.assertSoftly
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec

class SpdxExpressionTest : WordSpec() {
    private val yamlMapper = YAMLMapper()

    init {
        "spdxLicenses()" should {
            "contain all valid SPDX licenses" {
                val expression = "MIT OR (invalid1 AND Apache-2.0 WITH exp) AND (BSD-3-Clause OR invalid2 WITH exp)"
                val spdxExpression = SpdxExpression.parse(expression)

                val spdxLicenses = spdxExpression.spdxLicenses()

                spdxLicenses shouldBe enumSetOf(APACHE_2_0, BSD_3_CLAUSE, MIT)
            }
        }

        "toString()" should {
            "return the textual SPDX expression" {
                val expression = "license1+ AND (license2 WITH exception1 OR license3+) AND license4 WITH exception2"
                val spdxExpression = SpdxExpression.parse(expression)

                val spdxString = spdxExpression.toString()

                spdxString shouldBe expression
            }

            "not include unnecessary parenthesis" {
                val expression = "(license1 AND (license2 AND license3) AND (license4 OR (license5 WITH exception)))"
                val spdxExpression = SpdxExpression.parse(expression)

                val spdxString = spdxExpression.toString()

                spdxString shouldBe "license1 AND license2 AND license3 AND (license4 OR license5 WITH exception)"
            }
        }

        "A dummy SpdxExpression" should {
            val dummyExpression = "license1+ AND (license2 WITH exception1 OR license3+) AND license4 WITH exception2"

            "be serializable to a string representation" {
                val spdxExpression = SpdxExpression.parse(dummyExpression)

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
                            SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license2"),
                                SpdxOperator.WITH,
                                SpdxLicenseExceptionExpression("exception1")
                            ),
                            SpdxOperator.OR,
                            SpdxLicenseIdExpression("license3", true)
                        )
                    ),
                    SpdxOperator.AND,
                    SpdxCompoundExpression(
                        SpdxLicenseIdExpression("license4"),
                        SpdxOperator.WITH,
                        SpdxLicenseExceptionExpression("exception2")
                    )
                )
            }

            "be valid in lenient mode" {
                SpdxExpression.parse(dummyExpression, Strictness.ALLOW_ANY)
            }

            "be invalid in deprecated mode" {
                shouldThrow<SpdxException> {
                    SpdxExpression.parse(dummyExpression, Strictness.ALLOW_DEPRECATED)
                }
            }

            "be invalid in strict mode" {
                shouldThrow<SpdxException> {
                    SpdxExpression.parse(dummyExpression, Strictness.ALLOW_CURRENT)
                }
            }
        }

        "An SpdxExpression with deprecated identifiers" should {
            val deprecatedExpression = "GPL-1.0+"
            val deprecatedExpressionWithException = "GPL-2.0-with-classpath-exception"

            "be valid in lenient mode" {
                assertSoftly {
                    SpdxExpression.parse(deprecatedExpression, Strictness.ALLOW_ANY)
                    SpdxExpression.parse(deprecatedExpressionWithException, Strictness.ALLOW_ANY)
                }
            }

            "be valid in deprecated mode" {
                assertSoftly {
                    SpdxExpression.parse(deprecatedExpression, Strictness.ALLOW_DEPRECATED)
                    SpdxExpression.parse(deprecatedExpressionWithException, Strictness.ALLOW_DEPRECATED)
                }
            }

            "be invalid in strict mode" {
                assertSoftly {
                    shouldThrow<SpdxException> {
                        SpdxExpression.parse(deprecatedExpression, Strictness.ALLOW_CURRENT)
                    }
                    shouldThrow<SpdxException> {
                        SpdxExpression.parse(deprecatedExpressionWithException, Strictness.ALLOW_CURRENT)
                    }
                }
            }
        }

        "An SpdxExpression with current identifiers" should {
            val currentExpression = "GPL-1.0-only"
            val currentExpressionWithException = "GPL-2.0-or-later WITH Classpath-exception-2.0"

            "be valid in lenient mode" {
                assertSoftly {
                    SpdxExpression.parse(currentExpression, Strictness.ALLOW_ANY)
                    SpdxExpression.parse(currentExpressionWithException, Strictness.ALLOW_ANY)
                }
            }

            "be valid in deprecated mode" {
                assertSoftly {
                    SpdxExpression.parse(currentExpression, Strictness.ALLOW_DEPRECATED)
                    SpdxExpression.parse(currentExpressionWithException, Strictness.ALLOW_DEPRECATED)
                }
            }

            "be valid in strict mode" {
                assertSoftly {
                    SpdxExpression.parse(currentExpression, Strictness.ALLOW_CURRENT)
                    SpdxExpression.parse(currentExpressionWithException, Strictness.ALLOW_CURRENT)
                }
            }
        }

        "The expression parser" should {
            "work for deprecated license identifiers" {
                assertSoftly {
                    SpdxExpression.parse("eCos-2.0") shouldBe SpdxLicenseIdExpression("eCos-2.0")
                    SpdxExpression.parse("Nunit") shouldBe SpdxLicenseIdExpression("Nunit")
                    SpdxExpression.parse("StandardML-NJ") shouldBe SpdxLicenseIdExpression("StandardML-NJ")
                    SpdxExpression.parse("wxWindows") shouldBe SpdxLicenseIdExpression("wxWindows")
                }
            }

            "normalize the case of SPDX licenses" {
                SpdxLicense.values().forEach {
                    SpdxExpression.parse(it.id.toLowerCase()).normalize() shouldBe it.toExpression()
                }
            }

            "normalize deprecated licenses to non-deprecated ones" {
                assertSoftly {
                    SpdxExpression.parse("AGPL-1.0").normalize() shouldBe AGPL_1_0_ONLY.toExpression()
                    SpdxExpression.parse("AGPL-1.0+").normalize() shouldBe
                            SpdxLicenseIdExpression("AGPL-1.0-or-later", true)

                    SpdxExpression.parse("AGPL-3.0").normalize() shouldBe AGPL_3_0_ONLY.toExpression()
                    SpdxExpression.parse("AGPL-3.0+").normalize() shouldBe
                            SpdxLicenseIdExpression("AGPL-3.0-or-later", true)

                    SpdxExpression.parse("GFDL-1.1").normalize() shouldBe GFDL_1_1_ONLY.toExpression()
                    SpdxExpression.parse("GFDL-1.1+").normalize() shouldBe
                            SpdxLicenseIdExpression("GFDL-1.1-or-later", true)

                    SpdxExpression.parse("GFDL-1.2").normalize() shouldBe GFDL_1_2_ONLY.toExpression()
                    SpdxExpression.parse("GFDL-1.2+").normalize() shouldBe
                            SpdxLicenseIdExpression("GFDL-1.2-or-later", true)

                    SpdxExpression.parse("GFDL-1.3").normalize() shouldBe GFDL_1_3_ONLY.toExpression()
                    SpdxExpression.parse("GFDL-1.3+").normalize() shouldBe
                            SpdxLicenseIdExpression("GFDL-1.3-or-later", true)

                    SpdxExpression.parse("GPL-1.0").normalize() shouldBe GPL_1_0_ONLY.toExpression()
                    SpdxExpression.parse("GPL-1.0+").normalize() shouldBe
                            SpdxLicenseIdExpression("GPL-1.0-or-later", true)

                    SpdxExpression.parse("GPL-2.0").normalize() shouldBe GPL_2_0_ONLY.toExpression()
                    SpdxExpression.parse("GPL-2.0+").normalize() shouldBe
                            SpdxLicenseIdExpression("GPL-2.0-or-later", true)

                    SpdxExpression.parse("GPL-3.0").normalize() shouldBe GPL_3_0_ONLY.toExpression()
                    SpdxExpression.parse("GPL-3.0+").normalize() shouldBe
                            SpdxLicenseIdExpression("GPL-3.0-or-later", true)

                    SpdxExpression.parse("LGPL-2.0").normalize() shouldBe LGPL_2_0_ONLY.toExpression()
                    SpdxExpression.parse("LGPL-2.0+").normalize() shouldBe
                            SpdxLicenseIdExpression("LGPL-2.0-or-later", true)

                    SpdxExpression.parse("LGPL-2.1").normalize() shouldBe LGPL_2_1_ONLY.toExpression()
                    SpdxExpression.parse("LGPL-2.1+").normalize() shouldBe
                            SpdxLicenseIdExpression("LGPL-2.1-or-later", true)

                    SpdxExpression.parse("LGPL-3.0").normalize() shouldBe LGPL_3_0_ONLY.toExpression()
                    SpdxExpression.parse("LGPL-3.0+").normalize() shouldBe
                            SpdxLicenseIdExpression("LGPL-3.0-or-later", true)

                    // These have no known successors, so just keep them.
                    SpdxExpression.parse("eCos-2.0").normalize() shouldBe ECOS_2_0.toExpression()
                    SpdxExpression.parse("Nunit").normalize() shouldBe NUNIT.toExpression()
                    SpdxExpression.parse("StandardML-NJ").normalize() shouldBe STANDARDML_NJ.toExpression()
                    SpdxExpression.parse("wxWindows").normalize() shouldBe WXWINDOWS.toExpression()
                }
            }

            "normalize deprecated license exceptions to non-deprecated ones" {
                assertSoftly {
                    SpdxExpression.parse("GPL-2.0-with-autoconf-exception").normalize() shouldBe
                            (GPL_2_0_ONLY with AUTOCONF_EXCEPTION_2_0)
                    SpdxExpression.parse("GPL-2.0-with-bison-exception").normalize() shouldBe
                            (GPL_2_0_ONLY with BISON_EXCEPTION_2_2)
                    SpdxExpression.parse("GPL-2.0-with-classpath-exception").normalize() shouldBe
                            (GPL_2_0_ONLY with CLASSPATH_EXCEPTION_2_0)
                    SpdxExpression.parse("GPL-2.0-with-font-exception").normalize() shouldBe
                            (GPL_2_0_ONLY with FONT_EXCEPTION_2_0)
                    SpdxExpression.parse("GPL-2.0-with-GCC-exception").normalize() shouldBe
                            (GPL_2_0_ONLY with GCC_EXCEPTION_2_0)
                    SpdxExpression.parse("GPL-3.0-with-autoconf-exception").normalize() shouldBe
                            (GPL_3_0_ONLY with AUTOCONF_EXCEPTION_3_0)
                    SpdxExpression.parse("GPL-3.0-with-GCC-exception").normalize() shouldBe
                            (GPL_3_0_ONLY with GCC_EXCEPTION_3_1)
                }
            }
        }

        "decompose" should {
            fun String.decompose() = SpdxExpression.parse(this).decompose().map { it.toString() }

            "not split-up compound expressions with a WITH operator" {
                "GPL-2.0-or-later WITH Classpath-exception-2.0".decompose() shouldContainExactlyInAnyOrder listOf(
                    "GPL-2.0-or-later WITH Classpath-exception-2.0"
                )
            }

            "split-up compound expressions with AND or OR operator but not ones with WITH operator" {
                "GPL-2.0-or-later WITH Classpath-exception-2.0 AND MIT"
                    .decompose() shouldContainExactlyInAnyOrder listOf(
                        "GPL-2.0-or-later WITH Classpath-exception-2.0",
                        "MIT"
                    )
            }

            "work with LicenseRef-* identifiers" {
                "LicenseRef-gpl-2.0-custom WITH Classpath-exception-2.0 AND LicenseRef-scancode-commercial-license"
                    .decompose() shouldContainExactlyInAnyOrder listOf(
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
                    .decompose() shouldContainExactlyInAnyOrder listOf(
                    "GPL-2.0-or-later WITH Classpath-exception-2.0",
                    "GPL-2.0-or-later"
                )
            }
        }
    }
}
