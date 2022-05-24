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

import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME

/**
 * A description of the source code repository that was used as input for ORT.
 */
data class Repository(
    /**
     * Original VCS-related information from the working tree containing the analyzer root.
     */
    val vcs: VcsInfo,

    /**
     * Processed VCS-related information from the working tree containing the analyzer root that has e.g. common
     * mistakes corrected.
     */
    val vcsProcessed: VcsInfo = vcs.normalize(),

    /**
     * A map of nested repositories, for example Git submodules or Git-Repo modules. The key is the path to the
     * nested repository relative to the root of the main repository.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val nestedRepositories: Map<String, VcsInfo> = emptyMap(),

    /**
     * The configuration of the repository, parsed from [ORT_REPO_CONFIG_FILENAME].
     */
    val config: RepositoryConfiguration = RepositoryConfiguration()
) {
    companion object {
        /**
         * A constant for a [Repository] where all properties are empty strings.
         */
        @JvmField
        val EMPTY = Repository(
            vcs = VcsInfo.EMPTY,
            vcsProcessed = VcsInfo.EMPTY,
            nestedRepositories = emptyMap(),
            config = RepositoryConfiguration()
        )
    }

    /**
     * Return the path of [vcs] relative to [Repository.vcs], or null if [vcs] is neither [Repository.vcs] nor contained
     * in [nestedRepositories].
     */
    fun getRelativePath(vcs: VcsInfo): String? {
        fun VcsInfo.matches(other: VcsInfo) =
            type == other.type && url == other.url && revision == other.revision

        val normalizedVcs = vcs.normalize()

        if (vcsProcessed.matches(normalizedVcs)) return ""

        return nestedRepositories.entries.find { (_, nestedVcs) -> nestedVcs.normalize().matches(normalizedVcs) }?.key
    }
}
