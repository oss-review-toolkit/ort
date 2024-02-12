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

package org.ossreviewtoolkit.model.config.snippet

import org.ossreviewtoolkit.model.TextLocation

/**
 * A snippet choice for a given source file.
 */
data class SnippetChoice(
    /**
     * The source file criteria for which the snippet choice is made.
     */
    val given: Given,

    /**
     * The snippet criteria to make the snippet choice.
     */
    val choice: Choice
)

/**
 * A source file criteria for which the snippet choice is made.
 */
data class Given(
    /**
     * The source file for which the snippet choice is made.
     */
    val sourceLocation: TextLocation
)

/**
 * A snippet criteria to make the snippet choice.
 */
data class Choice(
    /**
     * The purl of the snippet chosen by this snippet choice. If [reason] is [SnippetChoiceReason.NO_RELEVANT_FINDING],
     * it is null.
     */
    val purl: String? = null,

    /**
     * The reason why this snippet choice was made.
     */
    val reason: SnippetChoiceReason,

    /**
     * An optional comment describing the snippet choice.
     */
    val comment: String? = null
)
