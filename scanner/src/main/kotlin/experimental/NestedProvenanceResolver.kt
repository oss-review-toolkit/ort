/*
 * Copyright (C) 2021 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.experimental

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance

/**
 * The [NestedProvenanceResolver] provides a function to resolve nested provenances.
 */
interface NestedProvenanceResolver {
    /**
     * Resolve nested [Provenance]s of the provided [provenance]. For an [ArtifactProvenance] the returned
     * [NestedProvenance] always contains only the provided [ArtifactProvenance]. For a [RepositoryProvenance] the
     * resolver looks for nested repositories, for example Git submodules or Mercurial subrepositories.
     */
    fun resolveNestedProvenance(provenance: KnownProvenance): NestedProvenance
}

/**
 * The default implementation of [NestedProvenanceResolver].
 */
class DefaultNestedProvenanceResolver(private val workingTreeCache: WorkingTreeCache) : NestedProvenanceResolver {
    override fun resolveNestedProvenance(provenance: KnownProvenance): NestedProvenance {
        return when (provenance) {
            is ArtifactProvenance -> NestedProvenance(root = provenance, subRepositories = emptyMap())
            is RepositoryProvenance -> runBlocking { resolveNestedRepository(provenance) }
        }
    }

    private suspend fun resolveNestedRepository(provenance: RepositoryProvenance): NestedProvenance {
        return workingTreeCache.use(provenance.vcsInfo) { vcs, workingTree ->
            vcs.updateWorkingTree(
                workingTree,
                provenance.resolvedRevision,
                path = provenance.vcsInfo.path,
                recursive = true
            ).onFailure { throw it }

            val subRepositories = workingTree.getNested().mapValues { (_, nestedVcs) ->
                // TODO: Verify that the revision is always a resolved revision.
                RepositoryProvenance(nestedVcs, nestedVcs.revision)
            }

            NestedProvenance(root = provenance, subRepositories = subRepositories)
        }
    }
}
