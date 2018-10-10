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

import com.fasterxml.jackson.annotation.JsonIgnore

import com.here.ort.model.Error

/**
 * Defines the resolution of an error. This can be used to silence false positives, or errors that have been identified
 * as not being relevant.
 */
data class ErrorResolution(
    /**
     * A regular expression string to match the messages of errors to resolve. Will be converted to a [Regex] using
     * [RegexOption.DOT_MATCHES_ALL].
     */
    val message: String,

    /**
     * The reason why the errors is resolved.
     */
    val reason: ErrorResolutionReason,

    /**
     * A comment to further explain why the [reason] is applicable here.
     */
    val comment: String
) {
    @JsonIgnore
    private val regex = Regex(message, RegexOption.DOT_MATCHES_ALL)

    /**
     * True if [message] matches the message of [error].
     */
    fun matches(error: Error) = regex.matches(error.message)
}
