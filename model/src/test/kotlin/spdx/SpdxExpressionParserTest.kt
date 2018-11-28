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
                val spdxExpression = parseSpdxExpression("spdx.license-id")

                spdxExpression shouldBe SpdxLicenseIdExpression("spdx.license-id", false)
            }

            "parse a license id starting with a digit correctly" {
                val spdxExpression = parseSpdxExpression("0license")

                spdxExpression shouldBe SpdxLicenseIdExpression("0license", false)
            }

            "parse a license id with any later version correctly" {
                val spdxExpression = parseSpdxExpression("license+")

                spdxExpression shouldBe SpdxLicenseIdExpression("license", true)
            }

            "parse a document ref correctly" {
                val spdxExpression = parseSpdxExpression("DocumentRef-license")

                spdxExpression shouldBe SpdxLicenseRefExpression("DocumentRef-license")
            }

            "parse a license ref correctly" {
                val spdxExpression = parseSpdxExpression("LicenseRef-license")

                spdxExpression shouldBe SpdxLicenseRefExpression("LicenseRef-license")
            }

            "parse a complex expression correctly" {
                val expression = "license1+ and ((license2 with exception1) OR license3+ AND license4 WITH exception2)"

                val spdxExpression = parseSpdxExpression(expression)

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxLicenseIdExpression("license1", true),
                        SpdxOperator.AND,
                        SpdxCompoundExpression(
                                SpdxCompoundExpression(
                                        SpdxLicenseIdExpression("license2", false),
                                        SpdxOperator.WITH,
                                        SpdxLicenseExceptionExpression("exception1")
                                ),
                                SpdxOperator.OR,
                                SpdxCompoundExpression(
                                        SpdxLicenseIdExpression("license3", true),
                                        SpdxOperator.AND,
                                        SpdxCompoundExpression(
                                                SpdxLicenseIdExpression("license4", false),
                                                SpdxOperator.WITH,
                                                SpdxLicenseExceptionExpression("exception2")
                                        )
                                )
                        )
                )
            }

            "bind + stronger than WITH" {
                val spdxExpression = parseSpdxExpression("license+ WITH exception")

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxLicenseIdExpression("license", true),
                        SpdxOperator.WITH,
                        SpdxLicenseExceptionExpression("exception")
                )
            }

            "bind WITH stronger than AND" {
                val spdxExpression = parseSpdxExpression("license1 AND license2 WITH exception")

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxLicenseIdExpression("license1", false),
                        SpdxOperator.AND,
                        SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license2", false),
                                SpdxOperator.WITH,
                                SpdxLicenseExceptionExpression("exception")
                        )
                )
            }

            "bind AND stronger than OR" {
                val spdxExpression = parseSpdxExpression("license1 OR license2 AND license3")

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxLicenseIdExpression("license1", false),
                        SpdxOperator.OR,
                        SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license2", false),
                                SpdxOperator.AND,
                                SpdxLicenseIdExpression("license3", false)
                        )
                )
            }

            "bind the same operator in order" {
                val spdxExpression = parseSpdxExpression("license1 AND license2 AND license3")

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license1", false),
                                SpdxOperator.AND,
                                SpdxLicenseIdExpression("license2", false)
                        ),
                        SpdxOperator.AND,
                        SpdxLicenseIdExpression("license3", false)
                )
            }

            "respect parentheses for binding strength of operators" {
                val expression = "(license1 OR license2) AND license3"

                val spdxExpression = parseSpdxExpression(expression)

                spdxExpression shouldBe SpdxCompoundExpression(
                        SpdxCompoundExpression(
                                SpdxLicenseIdExpression("license1", false),
                                SpdxOperator.OR,
                                SpdxLicenseIdExpression("license2", false)
                        ),
                        SpdxOperator.AND,
                        SpdxLicenseIdExpression("license3", false)
                )
            }

            "fail if + is used in an exception expression" {
                val exception = shouldThrow<Error> {
                    parseSpdxExpression("license WITH exception+")
                }

                exception.message shouldBe "SpdxExpression has invalid amount of children: '3'"
            }

            "fail if a compound expression is used before WITH" {
                val exception = shouldThrow<Error> {
                    parseSpdxExpression("(license1 AND license2) WITH exception")
                }

                exception.message shouldBe "SpdxExpression has invalid amount of children: '3'"
            }

            "fail on an invalid symbol" {
                val exception = shouldThrow<Error> {
                    parseSpdxExpression("/")
                }

                exception.message shouldBe "token recognition error at: '/'"
            }

            "fail on a syntax error" {
                val exception = shouldThrow<Error> {
                    parseSpdxExpression("((")
                }

                exception.message shouldBe "Illegal operator '(' in expression '((<missing ')'>'."
            }
        }
    }
}
