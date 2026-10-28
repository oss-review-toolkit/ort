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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class SpdxCompoundExpressionTest : WordSpec({
    "Creating a compound expression" should {
        "fail if the expression has less than two children" {
            shouldThrow<IllegalArgumentException> {
                SpdxCompoundExpression(SpdxOperator.AND, emptyList())
            }

            shouldThrow<IllegalArgumentException> {
                SpdxCompoundExpression(SpdxOperator.AND, listOf(SpdxLicenseIdExpression("A")))
            }
        }
    }

    "Simplifying a compound expression" should {
        "inline nested children of the same operator" {
            val expression = SpdxCompoundExpression(
                SpdxOperator.AND,
                listOf(
                    SpdxCompoundExpression(
                        SpdxOperator.AND,
                        listOf(
                            SpdxLicenseIdExpression("A"),
                            SpdxCompoundExpression(
                                SpdxOperator.AND,
                                listOf(
                                    SpdxLicenseIdExpression("A"),
                                    SpdxLicenseIdExpression("B")
                                )
                            )
                        )
                    ),
                    SpdxLicenseIdExpression("B")
                )
            )

            // Compare string representations to not rely on semantic equality.
            expression.simplify().sorted().toString() shouldBe "A AND B"
        }

        "create a single expression for equal AND-operands" {
            val expression = SpdxCompoundExpression(
                SpdxOperator.AND,
                listOf(
                    SpdxLicenseIdExpression("A"),
                    SpdxLicenseIdExpression("A")
                )
            )

            // Compare string representations to not rely on semantic equality.
            expression.simplify().toString() shouldBe "A"
        }

        "create a single expression for equal OR-operands" {
            val expression = SpdxCompoundExpression(
                SpdxOperator.OR,
                listOf(
                    SpdxLicenseIdExpression("A"),
                    SpdxLicenseIdExpression("A")
                )
            )

            // Compare string representations to not rely on semantic equality.
            expression.simplify().toString() shouldBe "A"
        }

        "remove redundant choices with a nested expression at the beginning" {
            val expression = "(B OR A) AND A AND B AND B AND A".toSpdx()

            // Compare string representations to not rely on semantic equality.
            expression.simplify().sorted().toString() shouldBe "A AND B"
        }

        "remove redundant choices with a nested expression in the middle" {
            val expression = "B AND (B OR A) AND A AND A".toSpdx()

            // Compare string representations to not rely on semantic equality.
            expression.simplify().sorted().toString() shouldBe "A AND B"
        }

        "remove redundant choices with a nested expression at the end" {
            val expression = "A AND B AND B AND A AND (B OR A)".toSpdx()

            // Compare string representations to not rely on semantic equality.
            expression.simplify().sorted().toString() shouldBe "A AND B"
        }
    }
})
