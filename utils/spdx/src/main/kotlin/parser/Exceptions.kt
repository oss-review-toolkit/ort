/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.spdx.parser

import kotlin.reflect.KClass

import org.ossreviewtoolkit.utils.spdx.SpdxException

/**
 * An exception to indicate that an [SpdxExpressionLexer] error occurred.
 */
class SpdxExpressionLexerException(val char: Char, val position: Int) :
    SpdxException("Unexpected character '$char' at position $position.")

/**
 * An exception to indicate that an [SpdxExpressionParser] error occurred. [token] is the unexpected token that caused
 * the exception, if it is `null` that means the end of the input was reached unexpectedly. [expectedTokenTypes] are the
 * expected token types, if available.
 */
class SpdxExpressionParserException(
    val token: Token?,
    vararg val expectedTokenTypes: KClass<out Token> = emptyArray()
) : SpdxException(
    buildString {
        append("Unexpected token '$token'")

        if (expectedTokenTypes.size == 1) {
            append(", expected ${expectedTokenTypes.first().simpleName}")
        } else if (expectedTokenTypes.size > 1) {
            append(", expected one of ${expectedTokenTypes.joinToString { it.simpleName.orEmpty() }}")
        }

        append(".")
    }
)
