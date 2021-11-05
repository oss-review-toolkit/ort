/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.utils.spdx.model

import org.ossreviewtoolkit.utils.spdx.InvalidLicenseChoiceException
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * An individual license choice.
 *
 * [given] is the complete license expression, or a sub-expression of the license, where [choice] is going to be applied
 * on. If no [given] is supplied, the [choice] will be applied to the complete expression of the package.
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
 */
data class SpdxLicenseChoice(
    val given: SpdxExpression?,
    val choice: SpdxExpression,
) {
    init {
        if (given?.isValidChoice(choice) == false) {
            throw InvalidLicenseChoiceException(
                "$choice is not a valid choice for $given. Valid choices are: ${given.validChoices()}."
            )
        }
    }
}
