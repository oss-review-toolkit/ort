/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.fossid.model.rules

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * An "ignore rule" allows specifying FossID which files need to be excluded from scan.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class IgnoreRule(
    /**
     * The id of the rule.
     */
    val id: Int,

    /**
     * The type of the rule.
     */
    val type: RuleType,

    /**
     * The value of the rule.
     */
    val value: String,

    /**
     * The id of the scan for which the rule is defined.
     */
    val scanId: Int,

    /**
     * The date the rule was last updated (e.g. 2022-01-05 14:59:22).
     */
    val updated: String
)
