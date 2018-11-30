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

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

sealed class SpdxExpression

data class SpdxCompoundExpression(
        val left: SpdxExpression,
        val operator: SpdxOperator,
        val right: SpdxExpression
) : SpdxExpression()

data class SpdxLicenseExceptionExpression(
        val id: String
) : SpdxExpression()

data class SpdxLicenseIdExpression(
        val id: String,
        val anyLaterVersion: Boolean = false
) : SpdxExpression()

data class SpdxLicenseRefExpression(
        val id: String
) : SpdxExpression()

enum class SpdxOperator(
        /**
         * The priority of the operator. An operator with a larger priority value binds stronger than an operator with a
         * lower priority value. Operators with the same priority bind left-associative.
         */
        val priority: Int
) {
    AND(1),
    OR(0),
    WITH(2)
}

fun parseSpdxExpression(expression: String): SpdxExpression {
    val charStream = CharStreams.fromString(expression)
    val lexer = SpdxExpressionLexer(charStream)

    lexer.addErrorListener(object : BaseErrorListener() {
        override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                e: RecognitionException?
        ) {
            throw SpdxException(msg)
        }
    })

    val tokenStream = CommonTokenStream(lexer)
    val parser = SpdxExpressionParser(tokenStream)
    val visitor = SpdxExpressionDefaultVisitor()

    return visitor.visit(parser.licenseExpression())
}
