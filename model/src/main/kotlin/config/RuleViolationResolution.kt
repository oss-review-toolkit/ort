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

package org.ossreviewtoolkit.model.config

import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.utils.sanitizeMessage

/**
 * Defines the resolution of a rule violation. This can be used to silence rule violations that have been identified
 * as not being relevant or are acceptable / approved.
 */
data class RuleViolationResolution(
    /**
     * A regular expression string to match the messages of rule violations to resolve. Will be converted to
     * a [Regex] using
     * [RegexOption.DOT_MATCHES_ALL].
     */
    val message: String,

    /**
     * The reason why the rule violation is resolved.
     */
    val reason: RuleViolationResolutionReason,

    /**
     * A comment to further explain why the [reason] is applicable here.
     */
    val comment: String
) {
    private val regex = Regex(message.sanitizeMessage(), RegexOption.DOT_MATCHES_ALL)

    /**
     * True if [message] matches the message of [error].
     */
    fun matches(violation: RuleViolation) = regex.matches(violation.message.sanitizeMessage())
}
