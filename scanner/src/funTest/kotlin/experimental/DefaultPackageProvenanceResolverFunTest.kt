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

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class DefaultPackageProvenanceResolverFunTest : WordSpec() {
    private val workingTreeCache = DefaultWorkingTreeCache()
    private val resolver = DefaultPackageProvenanceResolver(workingTreeCache)

    private val sourceArtifactUrl =
        "https://github.com/oss-review-toolkit/ort-test-data-npm/blob/test-1.0.0/README.md"
    private val repositoryUrl = "https://github.com/oss-review-toolkit/ort-test-data-npm"

    override fun afterSpec(spec: Spec) {
        runBlocking { workingTreeCache.shutdown() }
    }

    init {
        "Resolving an artifact provenance" should {
            "Succeed if the artifact exists" {
                val pkg = Package.EMPTY.copy(
                    sourceArtifact = RemoteArtifact(
                        url = sourceArtifactUrl,
                        hash = Hash.NONE
                    )
                )

                resolver.resolveProvenance(pkg, listOf(SourceCodeOrigin.ARTIFACT)) shouldBe
                        ArtifactProvenance(pkg.sourceArtifact)
            }

            "Fail if the artifact does not exist" {
                val pkg = Package.EMPTY.copy(
                    sourceArtifact = RemoteArtifact(
                        url = "$sourceArtifactUrl.invalid",
                        hash = Hash.NONE
                    )
                )

                resolver.resolveProvenance(pkg, listOf(SourceCodeOrigin.ARTIFACT)) shouldBe UnknownProvenance
            }
        }

        "Resolving a repository provenance" should {
            "Succeed if the revision is correct" {
                val pkg = Package.EMPTY.copy(
                    vcsProcessed = VcsInfo(
                        type = VcsType.GIT,
                        url = repositoryUrl,
                        revision = "ad0367b7b9920144a47b8d30cc0c84cea102b821"
                    )
                )

                resolver.resolveProvenance(pkg, listOf(SourceCodeOrigin.VCS)) shouldBe
                        RepositoryProvenance(
                            vcsInfo = pkg.vcsProcessed,
                            resolvedRevision = "ad0367b7b9920144a47b8d30cc0c84cea102b821"
                        )
            }

            "Resolve a tag name to a commit" {
                val pkg = Package.EMPTY.copy(
                    vcsProcessed = VcsInfo(
                        type = VcsType.GIT,
                        url = repositoryUrl,
                        revision = "test-1.0.0"
                    )
                )

                resolver.resolveProvenance(pkg, listOf(SourceCodeOrigin.VCS)) shouldBe
                        RepositoryProvenance(
                            vcsInfo = pkg.vcsProcessed,
                            resolvedRevision = "ad0367b7b9920144a47b8d30cc0c84cea102b821"
                        )
            }

            "Fail if the revision does not exist" {
                val pkg = Package.EMPTY.copy(
                    vcsProcessed = VcsInfo(
                        type = VcsType.GIT,
                        url = repositoryUrl,
                        revision = "non-existing-revision"
                    )
                )

                resolver.resolveProvenance(pkg, listOf(SourceCodeOrigin.VCS)) shouldBe UnknownProvenance
            }

            "Guess the correct tag for a package" {
                val pkg = Package.EMPTY.copy(
                    id = Identifier("NPM::test:1.0.0"),
                    vcsProcessed = VcsInfo(
                        type = VcsType.GIT,
                        url = repositoryUrl,
                        revision = ""
                    )
                )

                resolver.resolveProvenance(pkg, listOf(SourceCodeOrigin.VCS)) shouldBe
                        RepositoryProvenance(
                            vcsInfo = pkg.vcsProcessed,
                            resolvedRevision = "ad0367b7b9920144a47b8d30cc0c84cea102b821"
                        )
            }
        }

        "Resolving the provenance for multiple origins" should {
            "Respect the source code origin priority" {
                val pkg = Package.EMPTY.copy(
                    sourceArtifact = RemoteArtifact(
                        url = sourceArtifactUrl,
                        hash = Hash.NONE
                    ),
                    vcsProcessed = VcsInfo(
                        type = VcsType.GIT,
                        url = repositoryUrl,
                        revision = "ad0367b7b9920144a47b8d30cc0c84cea102b821"
                    )
                )

                resolver.resolveProvenance(pkg, listOf(SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS)) shouldBe
                        ArtifactProvenance(pkg.sourceArtifact)

                resolver.resolveProvenance(pkg, listOf(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT)) shouldBe
                        RepositoryProvenance(
                            vcsInfo = pkg.vcsProcessed,
                            resolvedRevision = "ad0367b7b9920144a47b8d30cc0c84cea102b821"
                        )
            }

            "Try all provided source code origins" {
                val pkg = Package.EMPTY.copy(
                    sourceArtifact = RemoteArtifact(
                        url = sourceArtifactUrl,
                        hash = Hash.NONE
                    )
                )

                resolver.resolveProvenance(pkg, listOf(SourceCodeOrigin.VCS, SourceCodeOrigin.ARTIFACT)) shouldBe
                        ArtifactProvenance(pkg.sourceArtifact)
            }
        }
    }
}
