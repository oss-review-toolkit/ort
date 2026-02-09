/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import com.fasterxml.jackson.annotation.JsonInclude

data class ScopeInclude(
    /**
     * A regular expression to match the names of scopes to include.
     */
    val pattern: String,

    /**
     * The reason why the scope is included, out of a predefined choice.
     */
    val reason: ScopeIncludeReason,

    /**
     * A comment to further explain why the [reason] is applicable here.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = ""
) {
    private val regex by lazy { Regex(pattern) }

    /**
     * True if [ScopeInclude.pattern] matches [scopeName].
     */
    fun matches(scopeName: String) = regex.matches(scopeName)
}
