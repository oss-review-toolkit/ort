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

package org.ossreviewtoolkit.model

import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression

data class RuleViolation(
    /**
     * The identifier of the rule that found this violation.
     */
    val rule: String,

    /**
     * The identifier of the package that caused this rule violation.
     */
    val pkg: Identifier?,

    /**
     * The name of the license that caused this rule violation. Can be null if the rule does not work on licenses.
     */
    val license: SpdxSingleLicenseExpression?,

    /**
     * The [source][LicenseSource] of the [license]. Can be null if the rule does not work on licenses.
     */
    val licenseSource: LicenseSource?,

    /**
     * The severity of the rule violation.
     */
    val severity: Severity,

    /**
     * A message explaining the rule violation.
     */
    val message: String,

    /**
     * A text explaining how the rule violation can be fixed. Reporters rendering this string should support the
     * [Markdown syntax](https://daringfireball.net/projects/markdown/syntax) to allow formatting of the text.
     */
    val howToFix: String
)
