/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

/**
 * This class holds the results of the provenance resolution for the package denoted by [id]. The provenance
 * resolution consists of root provenance resolution and nested provenance resolution - that is, determining the
 * sub-repositories of the root provenance. The information tells what has been scanned, or in case of an issues, what
 * problems happened during provenance resolution.
 */
data class ProvenanceResolutionResult(
    /**
     * The identifier of package.
     */
    val id: Identifier,

    /**
     * The resolved provenance of the package.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val packageProvenance: KnownProvenance? = null,

    /**
     * The (recursive) sub-repositories of [packageProvenance]. The listing is only empty if a
     * [packageProvenanceResolutionIssue] or a [nestedProvenanceResolutionIssue] happened.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val subRepositories: Map<String, VcsInfo> = emptyMap(),

    /**
     * The issue which happened during package provenance resolution.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val packageProvenanceResolutionIssue: Issue? = null,

    /**
     * The issue which happened during nested provenance resolution.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val nestedProvenanceResolutionIssue: Issue? = null
) {
    init {
        require((packageProvenance != null).xor(packageProvenanceResolutionIssue != null)) {
            "Either package provenance or package provenance resolution issue must be null, but not neither or both."
        }

        subRepositories.forEach { (path, vcsInfo) ->
            // TODO: Check if Git-Repo allows to include sub directories of repositories.
            require(vcsInfo.path.isEmpty()) {
                "The resolved sub-repository for package ${id.toCoordinates()} under path '$path' has a non-empty " +
                        "VCS path which is not allowed."
            }
        }

        if (packageProvenanceResolutionIssue != null) {
            require(nestedProvenanceResolutionIssue == null) {
                "Nested provenance resolution issue is not null, even though nested provenance resolution was not " +
                        "executed."
            }
        }
    }
}
