/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * An individual license choice.
 *
 * [given] is the complete license expression, or a sub-expression of the license, where [choice] is going to be applied
 * on. If no [given] is supplied, the [choice] will be applied to the complete expression of the package.
 *
 * If [given] and choice are both single license expressions, the choice is treated as a license version choice,
 * where given refers to an SPDX license which allows for choosing any later version, and choice refers to the actual
 * later version to chose which in turn may allow choosing a later version.
 *
 * e.g.: with [given] as complete expression
 * ```
 *  -> Complete license expression: (A OR B) AND C
 *  given: (A OR B) AND C
 *  choice: A AND C
 *  -> result: A AND C
 * ```
 *
 * e.g.: with [given] as sub-expression
 * ```
 *  -> Complete license expression: (A OR B) AND C
 *  given: (A OR B)
 *  choice: A
 *  -> result: A AND C
 * ```
 *
 * e.g.: without [given]
 * ```
 *  -> Complete license expression: (A OR B) AND (C OR D)
 *  choice: A AND C
 *  -> result: A AND C
 * ```
 *
 * e.g.: a license version choice
 * ```
 * -> Complete license expression: (A+ OR B) AND (C OR D)
 *  given: A+
 *  choice: A
 *  -> result: (A+ OR B) AND (C OR D)
 * ```
 */
data class SpdxLicenseChoice(
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val given: SpdxExpression? = null,
    val choice: SpdxExpression,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = ""
) {
    init {
        if (isVersionChoice()) {
            val isValid = SpdxOrLaterLicenseMapper.INSTANCE.isValidChoice(
                given as SpdxSingleLicenseExpression,
                choice as SpdxSingleLicenseExpression
            )

            if (!isValid) {
                throw InvalidLicenseVersionChoiceException("$choice is not a valid later version of $given.")
            }
        } else if (given?.isValidChoice(choice) == false) {
            throw InvalidLicenseChoiceException(
                "$choice is not a valid choice for $given. Valid choices are: ${given.validChoices()}."
            )
        }
    }

    fun isVersionChoice(): Boolean = given is SpdxSingleLicenseExpression && choice is SpdxSingleLicenseExpression
}
