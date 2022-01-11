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

/**
 * Bundles curation data for Version Control System information.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class VcsInfoCurationData(
    /**
     * The type of the VCS, for example Git, GitRepo, Mercurial, etc.
     */
    val type: VcsType? = null,

    /**
     * The URL to the VCS repository.
     */
    val url: String? = null,

    /**
     * The VCS-specific revision (tag, branch, SHA1) that the version of the package maps to.
     */
    val revision: String? = null,

    /**
     * The path inside the VCS to take into account, if any. The actual meaning depends on the VCS type. For
     * example, for Git only this subdirectory of the repository should be cloned, or for Git Repo it is
     * interpreted as the path to the manifest file.
     */
    val path: String? = null
) {
    /**
     * Merge with [other] curation data. If in question, data in this instance has precedence over data in the other
     * instance.
     */
    fun merge(other: VcsInfoCurationData) =
        VcsInfoCurationData(
            type = type ?: other.type,
            url = url ?: other.url,
            revision = revision ?: other.revision,
            path = path ?: other.path
        )
}
