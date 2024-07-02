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

import com.fasterxml.jackson.annotation.JsonIgnore
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
    val provenance: KnownProvenance,

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
            provenance = RepositoryProvenance(VcsInfo.EMPTY, ""),
            nestedRepositories = emptyMap(),
            config = RepositoryConfiguration()
        )
    }

    @JsonIgnore
    val vcs = if (provenance is RepositoryProvenance) {
        provenance.vcsInfo
    } else {
        VcsInfo.EMPTY
    }

    @JsonIgnore
    val vcsProcessed = if (provenance is RepositoryProvenance) {
        provenance.vcsInfo.normalize()
    } else {
        VcsInfo.EMPTY
    }

    /**
     * Return the path of [vcs] relative to [Repository.provenance], or null if [vcs] is neither
     * [Repository.provenance] nor contained in [nestedRepositories].
     */
    fun getRelativePath(vcs: VcsInfo): String? {
        if (this.provenance !is RepositoryProvenance) return null

        val normalizedVcs = vcs.normalize()

        if (vcsProcessed.equalsDisregardingPath(normalizedVcs)) return ""

        return nestedRepositories.entries.find { (_, nestedVcs) ->
            nestedVcs.normalize().equalsDisregardingPath(normalizedVcs)
        }?.key
    }
}
