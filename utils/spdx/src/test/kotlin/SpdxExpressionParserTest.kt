/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import io.kotest.matchers.shouldBe

class SpdxExpressionParserTest : WordSpec({
    "SpdxExpressionParser" should {
        "parse a license id correctly" {
            val actualExpression = SpdxExpression.parse("spdx.license-id")
            val expectedExpression = SpdxLicenseIdExpression("spdx.license-id")

            actualExpression shouldBe expectedExpression
        }

        "parse a license id starting with a digit correctly" {
            val actualExpression = SpdxExpression.parse("0license")
            val expectedExpression = SpdxLicenseIdExpression("0license")

            actualExpression shouldBe expectedExpression
        }

        "parse a license id with any later version correctly" {
            val actualExpression = SpdxExpression.parse("license+")
            val expectedExpression = SpdxLicenseIdExpression("license", orLaterVersion = true)

            actualExpression shouldBe expectedExpression
        }

        "parse a document ref correctly" {
            val actualExpression = SpdxExpression.parse("DocumentRef-document:LicenseRef-license")
            val expectedExpression = SpdxLicenseReferenceExpression("DocumentRef-document:LicenseRef-license")

            actualExpression shouldBe expectedExpression
        }

        "parse a license ref correctly" {
            val actualExpression = SpdxExpression.parse("LicenseRef-license")
            val expectedExpression = SpdxLicenseReferenceExpression("LicenseRef-license")

            actualExpression shouldBe expectedExpression
        }

        "parse a complex expression correctly" {
            val actualExpression = SpdxExpression.parse(
                "license1+ and ((license2 with exception1) OR license3+ AND license4 WITH exception2)"
            )
            val expectedExpression = SpdxCompoundExpression(
                SpdxLicenseIdExpression("license1", orLaterVersion = true),
                SpdxOperator.AND,
                SpdxCompoundExpression(
                    SpdxLicenseWithExceptionExpression(
                        SpdxLicenseIdExpression("license2"),
                        "exception1"
                    ),
                    SpdxOperator.OR,
                    SpdxCompoundExpression(
                        SpdxLicenseIdExpression("license3", orLaterVersion = true),
                        SpdxOperator.AND,
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
            val actualExpression = SpdxExpression.parse("license+ WITH exception")
            val expectedExpression = SpdxLicenseWithExceptionExpression(
                SpdxLicenseIdExpression("license", orLaterVersion = true),
                "exception"
            )

            actualExpression shouldBe expectedExpression
        }

        "bind WITH stronger than AND" {
            val actualExpression = SpdxExpression.parse("license1 AND license2 WITH exception")
            val expectedExpression = SpdxCompoundExpression(
                SpdxLicenseIdExpression("license1"),
                SpdxOperator.AND,
                SpdxLicenseWithExceptionExpression(
                    SpdxLicenseIdExpression("license2"),
                    "exception"
                )
            )

            actualExpression shouldBe expectedExpression
        }

        "bind AND stronger than OR" {
            val actualExpression = SpdxExpression.parse("license1 OR license2 AND license3")
            val expectedExpression = SpdxCompoundExpression(
                SpdxLicenseIdExpression("license1"),
                SpdxOperator.OR,
                SpdxCompoundExpression(
                    SpdxLicenseIdExpression("license2"),
                    SpdxOperator.AND,
                    SpdxLicenseIdExpression("license3")
                )
            )

            actualExpression shouldBe expectedExpression
        }

        "bind the and operator left associative" {
            val actualExpression = SpdxExpression.parse("license1 AND license2 AND license3")
            val expectedExpression = SpdxCompoundExpression(
                SpdxCompoundExpression(
                    SpdxLicenseIdExpression("license1"),
                    SpdxOperator.AND,
                    SpdxLicenseIdExpression("license2")
                ),
                SpdxOperator.AND,
                SpdxLicenseIdExpression("license3")
            )

            actualExpression shouldBe expectedExpression
        }

        "bind the or operator left associative" {
            val actualExpression = SpdxExpression.parse("license1 OR license2 OR license3")
            val expectedExpression = SpdxCompoundExpression(
                SpdxCompoundExpression(
                    SpdxLicenseIdExpression("license1"),
                    SpdxOperator.OR,
                    SpdxLicenseIdExpression("license2")
                ),
                SpdxOperator.OR,
                SpdxLicenseIdExpression("license3")
            )

            actualExpression shouldBe expectedExpression
        }

        "respect parentheses for binding strength of operators" {
            val actualExpression = SpdxExpression.parse("(license1 OR license2) AND license3")
            val expectedExpression = SpdxCompoundExpression(
                SpdxCompoundExpression(
                    SpdxLicenseIdExpression("license1"),
                    SpdxOperator.OR,
                    SpdxLicenseIdExpression("license2")
                ),
                SpdxOperator.AND,
                SpdxLicenseIdExpression("license3")
            )

            actualExpression shouldBe expectedExpression
        }

        "fail if + is used in an exception expression" {
            val exception = shouldThrow<SpdxException> {
                SpdxExpression.parse("license WITH exception+")
            }

            exception.message shouldBe "extraneous input '+' expecting <EOF>"
        }

        "fail if a compound expression is used before WITH" {
            val exception = shouldThrow<SpdxException> {
                SpdxExpression.parse("(license1 AND license2) WITH exception")
            }

            exception.message shouldBe "mismatched input 'WITH' expecting {AND, OR, ')', '+'}"
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

            exception.message shouldBe
                    "mismatched input '<EOF>' expecting {'(', DOCUMENTREFERENCE, LICENSEREFERENCE, IDSTRING}"
        }
    }
})
