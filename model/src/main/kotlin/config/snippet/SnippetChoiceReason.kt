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

/**
 * The reason for which the snippet choice has been made.
 */
enum class SnippetChoiceReason {
    /**
     * No relevant finding has been found for the corresponding source file. All snippets will be ignored.
     */
    NO_RELEVANT_FINDING,

    /**
     * One snippet finding is relevant for the corresponding source file. All other snippets will be ignored.
     */
    ORIGINAL_FINDING,

    /**
     * Other reason.
     */
    OTHER
}
