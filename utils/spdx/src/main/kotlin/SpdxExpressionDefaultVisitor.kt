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

import org.ossreviewtoolkit.utils.spdx.SpdxExpression.Strictness
import org.ossreviewtoolkit.utils.spdx.SpdxExpressionParser.CompoundExpressionContext
import org.ossreviewtoolkit.utils.spdx.SpdxExpressionParser.LicenseExpressionContext
import org.ossreviewtoolkit.utils.spdx.SpdxExpressionParser.LicenseIdExpressionContext
import org.ossreviewtoolkit.utils.spdx.SpdxExpressionParser.LicenseReferenceExpressionContext
import org.ossreviewtoolkit.utils.spdx.SpdxExpressionParser.SimpleExpressionContext

class SpdxExpressionDefaultVisitor(private val strictness: Strictness) :
    SpdxExpressionBaseVisitor<SpdxExpression>() {
    override fun visitLicenseExpression(ctx: LicenseExpressionContext): SpdxExpression =
        when (ctx.childCount) {
            2 -> visit(ctx.getChild(0))
            else -> throw SpdxException("SpdxExpression has invalid amount of children: '${ctx.childCount}'")
        }

    override fun visitCompoundExpression(ctx: CompoundExpressionContext): SpdxExpression =
        when (ctx.childCount) {
            1 -> visit(ctx.getChild(0))
            3 -> {
                if (ctx.getChild(0).text == "(" && ctx.getChild(2).text == ")") {
                    visit(ctx.getChild(1))
                } else {
                    val left = visit(ctx.getChild(0))
                    val operator = ctx.getChild(1).text

                    when (val uppercaseOperator = operator.uppercase()) {
                        SpdxExpression.WITH -> {
                            val right = ctx.getChild(2).text
                            SpdxLicenseWithExceptionExpression(left as SpdxSimpleExpression, right)
                                .apply { validate(strictness) }
                        }
                        else -> {
                            val right = visit(ctx.getChild(2))
                            val spdxOperator = try {
                                SpdxOperator.valueOf(uppercaseOperator)
                            } catch (e: IllegalArgumentException) {
                                throw SpdxException("Illegal operator '$operator' in expression '${ctx.text}'.", e)
                            }

                            SpdxCompoundExpression(left, spdxOperator, right)
                        }
                    }
                }
            }
            else -> throw SpdxException("SpdxCompoundExpression has invalid amount of children: '${ctx.childCount}'")
        }

    override fun visitSimpleExpression(ctx: SimpleExpressionContext): SpdxExpression =
        when (ctx.childCount) {
            1 -> visit(ctx.getChild(0))
            else -> throw SpdxException("SpdxSimpleExpression has invalid amount of children: '${ctx.childCount}'")
        }

    override fun visitLicenseIdExpression(ctx: LicenseIdExpressionContext): SpdxExpression =
        when (ctx.childCount) {
            1 -> SpdxLicenseIdExpression(ctx.text, ctx.text.endsWith("-or-later"))
            2 -> SpdxLicenseIdExpression(ctx.text.dropLast(1) /* drop the trailing "+" */, true)
            else -> throw SpdxException("SpdxLicenseIdExpression has invalid amount of children: '${ctx.childCount}'")
        }.apply { validate(strictness) }

    override fun visitLicenseReferenceExpression(ctx: LicenseReferenceExpressionContext): SpdxExpression =
        when (ctx.childCount) {
            1 -> SpdxLicenseReferenceExpression(ctx.text)
            else -> throw SpdxException(
                "SpdxLicenseReferenceExpression has invalid amount of children: '${ctx.childCount}'"
            )
        }.apply { validate(strictness) }
}
