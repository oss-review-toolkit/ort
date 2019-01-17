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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotlintest.assertSoftly
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec

class SpdxExpressionTest : WordSpec() {
    private val yamlMapper = ObjectMapper(YAMLFactory())

    init {
        "spdxLicenses()" should {
            "contain all valid SPDX licenses" {
                val expression = "MIT OR (invalid1 AND Apache-2.0 WITH exp) AND (BSD-3-Clause OR invalid2 WITH exp)"
                val spdxExpression = SpdxExpression.parse(expression)

                val spdxLicenses = spdxExpression.spdxLicenses()

                spdxLicenses shouldBe enumSetOf(SpdxLicense.APACHE_2_0, SpdxLicense.BSD_3_CLAUSE, SpdxLicense.MIT)
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

        "An SpdxExpression" should {
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

            "be invalid if it contains undefined license strings" {
                val spdxExpression = SpdxExpression.parse(dummyExpression)

                shouldThrow<SpdxException> {
                    spdxExpression.validate()
                }
            }

            "be valid if it only contains licenses, exceptions and LicenseRefs" {
                val validExpression = "(CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0) AND LicenseRef-aop-pd"
                val spdxExpression = SpdxExpression.parse(validExpression)

                // This should not throw SpdxException. Unfortunately there is not better way to check this as
                // https://github.com/kotlintest/kotlintest/issues/205 was never implemented.
                spdxExpression.validate()
            }
        }

        "The expression parser" should {
            "work for deprecated license identifiers" {
                assertSoftly {
                    SpdxExpression.parse("Nunit") shouldBe SpdxLicenseIdExpression("Nunit")
                    SpdxExpression.parse("StandardML-NJ") shouldBe SpdxLicenseIdExpression("StandardML-NJ")
                    SpdxExpression.parse("wxWindows") shouldBe SpdxLicenseIdExpression("wxWindows")
                }
            }
        }
    }
}
