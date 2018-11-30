/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.model.spdx

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec

class SpdxExpressionParserTest : WordSpec() {
    init {
        "SpdxExpressionParser" should {
            "parse a license id correctly" {
                val spdxExpression = SpdxExpression.parse("spdx.license-id")

                spdxExpression shouldBe SpdxLicenseIdExpression("spdx.license-id")
            }

            "parse a license id starting with a digit correctly" {
                val spdxExpression = SpdxExpression.parse("0license")

                spdxExpression shouldBe SpdxLicenseIdExpression("0license")
            }

            "parse a license id with any later version correctly" {
                val spdxExpression = SpdxExpression.parse("license+")

                spdxExpression shouldBe SpdxLicenseIdExpression("license", anyLaterVersion = true)
            }

            "parse a document ref correctly" {
                val spdxExpression = SpdxExpression.parse("DocumentRef-license")

                spdxExpression shouldBe SpdxLicenseRefExpression("DocumentRef-license")
            }

            "parse a license ref correctly" {
                val spdxExpression = SpdxExpression.parse("LicenseRef-license")

                spdxExpression shouldBe SpdxLicenseRefExpression("LicenseRef-license")
            }

            "parse a complex expression correctly" {
                val expression = "license1+ and ((license2 with exception1) OR license3+ AND license4 WITH exception2)"

                val spdxExpression = SpdxExpression.parse(expression)

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxLicenseIdExpression("license1", anyLaterVersion = true),
                        SpdxOperator.AND,
                        SpdxCompoundExpression(
                                SpdxCompoundExpression(
                                        SpdxLicenseIdExpression("license2"),
                                        SpdxOperator.WITH,
                                        SpdxLicenseExceptionExpression("exception1")
                                ),
                                SpdxOperator.OR,
                                SpdxCompoundExpression(
                                        SpdxLicenseIdExpression("license3", anyLaterVersion = true),
                                        SpdxOperator.AND,
                                        SpdxCompoundExpression(
                                                SpdxLicenseIdExpression("license4"),
                                                SpdxOperator.WITH,
                                                SpdxLicenseExceptionExpression("exception2")
                                        )
                                )
                        )
                )
            }

            "bind + stronger than WITH" {
                val spdxExpression = SpdxExpression.parse("license+ WITH exception")

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxLicenseIdExpression("license", anyLaterVersion = true),
                        SpdxOperator.WITH,
                        SpdxLicenseExceptionExpression("exception")
                )
            }

            "bind WITH stronger than AND" {
                val spdxExpression = SpdxExpression.parse("license1 AND license2 WITH exception")

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxLicenseIdExpression("license1"),
                        SpdxOperator.AND,
                        SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license2"),
                                SpdxOperator.WITH,
                                SpdxLicenseExceptionExpression("exception")
                        )
                )
            }

            "bind AND stronger than OR" {
                val spdxExpression = SpdxExpression.parse("license1 OR license2 AND license3")

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxLicenseIdExpression("license1"),
                        SpdxOperator.OR,
                        SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license2"),
                                SpdxOperator.AND,
                                SpdxLicenseIdExpression("license3")
                        )
                )
            }

            "bind the and operator left associative" {
                val spdxExpression = SpdxExpression.parse("license1 AND license2 AND license3")

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license1"),
                                SpdxOperator.AND,
                                SpdxLicenseIdExpression("license2")
                        ),
                        SpdxOperator.AND,
                        SpdxLicenseIdExpression("license3")
                )
            }

            "bind the or operator left associative" {
                val spdxExpression = SpdxExpression.parse("license1 OR license2 OR license3")

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license1"),
                                SpdxOperator.OR,
                                SpdxLicenseIdExpression("license2")
                        ),
                        SpdxOperator.OR,
                        SpdxLicenseIdExpression("license3")
                )
            }

            "respect parentheses for binding strength of operators" {
                val expression = "(license1 OR license2) AND license3"

                val spdxExpression = SpdxExpression.parse(expression)

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license1"),
                                SpdxOperator.OR,
                                SpdxLicenseIdExpression("license2")
                        ),
                        SpdxOperator.AND,
                        SpdxLicenseIdExpression("license3")
                )
            }

            "fail if + is used in an exception expression" {
                val exception = shouldThrow<SpdxException> {
                    SpdxExpression.parse("license WITH exception+")
                }

                exception.message shouldBe "SpdxExpression has invalid amount of children: '3'"
            }

            "fail if a compound expression is used before WITH" {
                val exception = shouldThrow<SpdxException> {
                    SpdxExpression.parse("(license1 AND license2) WITH exception")
                }

                exception.message shouldBe "SpdxExpression has invalid amount of children: '3'"
            }

            "fail on an invalid symbol" {
                val exception = shouldThrow<SpdxException> {
                    SpdxExpression.parse("/")
                }

                exception.message shouldBe "token recognition error at: '/'"
            }

            "fail on a syntax error" {
                val exception = shouldThrow<SpdxException> {
                    SpdxExpression.parse("((")
                }

                exception.message shouldBe "Illegal operator '(' in expression '((<missing ')'>'."
            }
        }
    }
}
