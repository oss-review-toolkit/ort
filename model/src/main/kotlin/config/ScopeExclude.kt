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

package com.here.ort.model.config

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer

/**
 * Defines a scope that should be excluded.
 */
data class ScopeExclude(
        /**
         * A regular expression to match the names of scopes to exclude.
         */
        @JsonSerialize(using = ToStringSerializer::class)
        val name: Regex,

        /**
         * The reason why the scope is excluded, out of a predefined choice.
         */
        val reason: ScopeExcludeReason,

        /**
         * A comment to further explain why the [reason] is applicable here.
         */
        val comment: String
) {
    constructor(name: String, reason: ScopeExcludeReason, comment: String) : this(Regex(name), reason, comment)

    /**
     * True if [ScopeExclude.name] matches [scopeName].
     */
    fun matches(scopeName: String) = name.matches(scopeName)
}
