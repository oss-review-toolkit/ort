/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
     * True if the [revision] was already resolved. Resolved means that the revision must be fixed and confirmed to be
     * correct.
     *
     * Fixed means that the revision must not be a moving reference. For example, in the case of Git it must be the SHA1
     * of a commit, not a branch or tag name, because those could be changed to reference a different revision.
     *
     * Confirmed to be correct means that there is reasonable certainty that the revision is correct. For example, if
     * the revision is provided by a package manager it should not be marked as resolved if it comes from metadata
     * provided by the user, because this could be wrong. But if the package manager confirms the revision somehow, for
     * example by downloading the source code during the installation of dependencies, it can be marked as resolved.
     */
    val isResolvedRevision: Boolean? = null,

    /**
     * The path inside the VCS to take into account, if any. The actual meaning depends on the VCS type. For
     * example, for Git only this subdirectory of the repository should be cloned, or for Git Repo it is
     * interpreted as the path to the manifest file.
     */
    val path: String? = null
)
