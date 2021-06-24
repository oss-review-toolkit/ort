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

import com.fasterxml.jackson.annotation.JsonInclude

import java.util.SortedSet

/**
 * A class that bundles all information generated during an analysis.
 */
data class ProjectAnalyzerResult(
    /**
     * The project that was analyzed. The tree of dependencies is implicitly contained in the scopes in the form
     * of package references.
     */
    val project: Project,

    /**
     * The set of identified packages used by the project.
     */
    val packages: SortedSet<Package>,

    /**
     * The list of issues that occurred during dependency resolution. Defaults to an empty list.
     * This property is not serialized if the list is empty for consistency with the issue properties in other classes,
     * even if this class is not serialized as part of an [OrtResult].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val issues: List<OrtIssue> = emptyList()
)
