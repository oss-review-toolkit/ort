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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.IOException

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.Os

class DefaultNestedProvenanceResolverFunTest : WordSpec() {
    private val workingTreeCache = DefaultWorkingTreeCache()
    private val resolver = DefaultNestedProvenanceResolver(DummyNestedProvenanceStorage(), workingTreeCache)

    override suspend fun afterSpec(spec: Spec) {
        workingTreeCache.shutdown()
    }

    init {
        "Resolving an artifact provenance" should {
            "Return only the artifact provenance" {
                val provenance = ArtifactProvenance(
                    sourceArtifact = RemoteArtifact(
                        url = "",
                        hash = Hash.NONE
                    )
                )

                resolver.resolveNestedProvenance(provenance) shouldBe
                        NestedProvenance(root = provenance, subRepositories = emptyMap())
            }
        }

        "Resolving a repository provenance" should {
            "Return the correct root provenance" {
                val provenance = RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        url = "https://github.com/oss-review-toolkit/ort-governance.git",
                        revision = "1440358978888bfc4b6fbf356e4e6be005c9a25e"
                    ),
                    resolvedRevision = "1440358978888bfc4b6fbf356e4e6be005c9a25e"
                )

                resolver.resolveNestedProvenance(provenance) shouldBe
                        NestedProvenance(root = provenance, subRepositories = emptyMap())
            }

            "Find recursive Git submodules" {
                val provenance = RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        url = "https://github.com/oss-review-toolkit/ort-test-data-git-submodules.git",
                        revision = "fcea94bab5835172e826afddb9f6427274c983b9"
                    ),
                    resolvedRevision = "fcea94bab5835172e826afddb9f6427274c983b9"
                )

                val nestedProvenance = resolver.resolveNestedProvenance(provenance)

                nestedProvenance.root shouldBe provenance
                nestedProvenance.subRepositories should containExactly(
                    "commons-text" to RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT,
                            url = "https://github.com/apache/commons-text.git",
                            revision = "7643b12421100d29fd2b78053e77bcb04a251b2e"
                        ),
                        resolvedRevision = "7643b12421100d29fd2b78053e77bcb04a251b2e"
                    ),
                    "test-data-npm" to RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT,
                            url = "https://github.com/oss-review-toolkit/ort-test-data-npm.git",
                            revision = "ad0367b7b9920144a47b8d30cc0c84cea102b821"
                        ),
                        resolvedRevision = "ad0367b7b9920144a47b8d30cc0c84cea102b821"
                    ),
                    "test-data-npm/isarray" to RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT,
                            url = "https://github.com/juliangruber/isarray.git",
                            revision = "63ea4ca0a0d6b0574d6a470ebd26880c3026db4a"
                        ),
                        resolvedRevision = "63ea4ca0a0d6b0574d6a470ebd26880c3026db4a"
                    ),
                    "test-data-npm/long.js" to RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT, url = "https://github.com/dcodeIO/long.js.git",
                            revision = "941c5c62471168b5d18153755c2a7b38d2560e58"
                        ),
                        resolvedRevision = "941c5c62471168b5d18153755c2a7b38d2560e58"
                    )
                )
            }

            "Find Git-Repo projects with recursive Git submodules".config(enabled = !Os.isWindows) {
                val provenance = RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT_REPO,
                        url = "https://github.com/oss-review-toolkit/ort-test-data-git-repo.git?manifest=manifest.xml",
                        revision = "31588aa8f8555474e1c3c66a359ec99e4cd4b1fa"
                    ),
                    resolvedRevision = "31588aa8f8555474e1c3c66a359ec99e4cd4b1fa"
                )

                val nestedProvenance = resolver.resolveNestedProvenance(provenance)

                nestedProvenance.root shouldBe provenance
                nestedProvenance.subRepositories should containExactly(
                    "spdx-tools" to RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT,
                            url = "https://github.com/spdx/tools",
                            revision = "e179fae47590eccedc46186ea0ce20cbade5fda7"
                        ),
                        resolvedRevision = "e179fae47590eccedc46186ea0ce20cbade5fda7"
                    ),
                    "submodules" to RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT,
                            url = "https://github.com/oss-review-toolkit/ort-test-data-git-submodules",
                            revision = "fcea94bab5835172e826afddb9f6427274c983b9"
                        ),
                        resolvedRevision = "fcea94bab5835172e826afddb9f6427274c983b9"
                    ),
                    "submodules/commons-text" to RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT,
                            url = "https://github.com/apache/commons-text.git",
                            revision = "7643b12421100d29fd2b78053e77bcb04a251b2e"
                        ),
                        resolvedRevision = "7643b12421100d29fd2b78053e77bcb04a251b2e"
                    ),
                    "submodules/test-data-npm" to RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT,
                            url = "https://github.com/oss-review-toolkit/ort-test-data-npm.git",
                            revision = "ad0367b7b9920144a47b8d30cc0c84cea102b821"
                        ),
                        resolvedRevision = "ad0367b7b9920144a47b8d30cc0c84cea102b821"
                    ),
                    "submodules/test-data-npm/isarray" to RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT,
                            url = "https://github.com/juliangruber/isarray.git",
                            revision = "63ea4ca0a0d6b0574d6a470ebd26880c3026db4a"
                        ),
                        resolvedRevision = "63ea4ca0a0d6b0574d6a470ebd26880c3026db4a"
                    ),
                    "submodules/test-data-npm/long.js" to RepositoryProvenance(
                        vcsInfo = VcsInfo(
                            type = VcsType.GIT,
                            url = "https://github.com/dcodeIO/long.js.git",
                            revision = "941c5c62471168b5d18153755c2a7b38d2560e58"
                        ),
                        resolvedRevision = "941c5c62471168b5d18153755c2a7b38d2560e58"
                    )
                )
            }

            "Throw an exception if the resolved revision does not exist" {
                val provenance = RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        url = "https://github.com/oss-review-toolkit/ort-governance.git",
                        revision = "fcea94bab5835172e826afddb9f6427274c983b9"
                    ),
                    resolvedRevision = "0000000000000000000000000000000000000000"
                )

                shouldThrow<IOException> {
                    resolver.resolveNestedProvenance(provenance) shouldBe
                            NestedProvenance(root = provenance, subRepositories = emptyMap())
                }
            }
        }
    }
}

private class DummyNestedProvenanceStorage : NestedProvenanceStorage {
    override fun readNestedProvenance(root: RepositoryProvenance): NestedProvenanceResolutionResult? = null
    override fun putNestedProvenance(root: RepositoryProvenance, result: NestedProvenanceResolutionResult) {
        /** no-op */
    }
}
