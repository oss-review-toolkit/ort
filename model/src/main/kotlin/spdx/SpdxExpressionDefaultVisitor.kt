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

class SpdxExpressionDefaultVisitor : SpdxExpressionBaseVisitor<SpdxExpression>() {
    override fun visitLicenseExpression(ctx: SpdxExpressionParser.LicenseExpressionContext): SpdxExpression {
        return when (ctx.childCount) {
            2 -> visit(ctx.getChild(0))
            else -> throw Error("SpdxExpression has invalid amount of children: '${ctx.childCount}'")
        }
    }

    override fun visitCompoundExpression(ctx: SpdxExpressionParser.CompoundExpressionContext): SpdxExpression {
        return when (ctx.childCount) {
            1 -> visit(ctx.getChild(0))
            3 -> {
                if (ctx.getChild(0).text == "(" && ctx.getChild(2).text == ")") {
                    visit(ctx.getChild(1))
                } else {
                    val left = visit(ctx.getChild(0))

                    val operatorName = ctx.getChild(1).text
                    val operator = try {
                        SpdxOperator.valueOf(operatorName.toUpperCase())
                    } catch (e: IllegalArgumentException) {
                        throw Error("Illegal operator '$operatorName' in expression '${ctx.text}'.")
                    }

                    val right = visit(ctx.getChild(2))

                    if (operator == SpdxOperator.WITH && right !is SpdxLicenseExceptionExpression) {
                        throw Error("Argument '$right' for WITH is not an SPDX license exception id in '${ctx.text}'.")
                    }

                    SpdxCompoundExpression(left, operator, right)
                }
            }
            else -> throw Error("SpdxCompoundExpression has invalid amount of children: '${ctx.childCount}'")
        }
    }

    override fun visitSimpleExpression(ctx: SpdxExpressionParser.SimpleExpressionContext): SpdxExpression {
        return when (ctx.childCount) {
            1 -> visit(ctx.getChild(0))
            else -> throw Error("SpdxSimpleExpression has invalid amount of children: '${ctx.childCount}'")
        }
    }

    override fun visitLicenseExceptionExpression(ctx: SpdxExpressionParser.LicenseExceptionExpressionContext)
            : SpdxExpression {
        return when (ctx.childCount) {
            1 -> SpdxLicenseExceptionExpression(ctx.text)
            else -> throw Error("SpdxLicenseExceptionExpression has invalid amount of children: '${ctx.childCount}'")
        }
    }

    override fun visitLicenseIdExpression(ctx: SpdxExpressionParser.LicenseIdExpressionContext): SpdxExpression {
        return when (ctx.childCount) {
            1 -> SpdxLicenseIdExpression(ctx.text, false)
            2 -> SpdxLicenseIdExpression(ctx.text.dropLast(1), true)
            else -> throw Error("SpdxLicenseIdExpression has invalid amount of children: '${ctx.childCount}'")
        }
    }

    override fun visitLicenseRefExpression(ctx: SpdxExpressionParser.LicenseRefExpressionContext): SpdxExpression {
        return when (ctx.childCount) {
            1 -> SpdxLicenseRefExpression(ctx.text)
            else -> throw Error("SpdxLicenseRefExpression has invalid amount of children: '${ctx.childCount}'")
        }
    }
}
