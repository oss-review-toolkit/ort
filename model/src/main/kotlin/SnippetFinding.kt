/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

/**
 * A class representing the snippet findings for a source file location. A snippet finding is a code snippet from
 * another origin, matching the code being scanned.
 * It is meant to be reviewed by an operator as it could be a false positive.
 */
data class SnippetFinding(
    /**
     * The text location in the scanned source file where the snippet has matched.
     */
    val sourceLocation: TextLocation,

    /**
     * The corresponding snippets.
     */
    val snippets: Set<Snippet>
)
