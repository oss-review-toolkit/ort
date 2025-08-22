/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.provenance

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

abstract class AbstractPackageProvenanceStorageFunTest(vararg listeners: TestListener) : WordSpec() {
    private lateinit var storage: PackageProvenanceStorage

    protected abstract fun createStorage(): PackageProvenanceStorage

    init {
        extensions(listeners.asList())

        beforeEach {
            storage = createStorage()
        }

        "Adding a result" should {
            "succeed for an artifact result" {
                val id = createIdentifier()
                val sourceArtifact = createRemoteArtifact()
                val result = ResolvedArtifactProvenance(createArtifactProvenance(sourceArtifact))

                storage.writeProvenance(id, sourceArtifact, result)

                storage.readProvenance(id, sourceArtifact) shouldBe result
            }

            "succeed for a repository result" {
                val id = createIdentifier()
                val vcs = createVcsInfo()
                val result = ResolvedRepositoryProvenance(createRepositoryProvenance(vcs), vcs.revision, true)

                storage.writeProvenance(id, vcs, result)

                storage.readProvenance(id, vcs) shouldBe result
            }

            "succeed for a failed result" {
                val id = createIdentifier()
                val vcs = createVcsInfo()
                val result = UnresolvedPackageProvenance("message")

                storage.writeProvenance(id, vcs, result)

                storage.readProvenance(id, vcs) shouldBe result
            }

            "overwrite a previously stored result" {
                val id = createIdentifier()
                val vcs = createVcsInfo()
                val result1 = UnresolvedPackageProvenance("message")
                val result2 = ResolvedRepositoryProvenance(createRepositoryProvenance(vcs), vcs.revision, true)

                storage.writeProvenance(id, vcs, result1)
                storage.writeProvenance(id, vcs, result2)

                storage.readProvenance(id, vcs) shouldBe result2
            }
        }

        "Reading all results" should {
            "return all stored results" {
                val id = createIdentifier()

                val sourceArtifact = createRemoteArtifact()
                val artifactResult = ResolvedArtifactProvenance(createArtifactProvenance(sourceArtifact))
                storage.writeProvenance(id, sourceArtifact, artifactResult)

                val vcs = createVcsInfo()
                val vcsResult = ResolvedRepositoryProvenance(createRepositoryProvenance(vcs), vcs.revision, true)
                storage.writeProvenance(id, vcs, vcsResult)

                storage.readProvenances(id) should containExactlyInAnyOrder(artifactResult, vcsResult)
            }
        }
    }
}

private fun createIdentifier() = Identifier("Maven:org.apache.logging.log4j:log4j-api:2.14.1")

private fun createRemoteArtifact() =
    RemoteArtifact(
        url = "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.14.1/log4j-api-2.14.1-sources.jar",
        hash = Hash("b2327c47ca413c1ec183575b19598e281fcd74d8", HashAlgorithm.SHA1)
    )

private fun createArtifactProvenance(sourceArtifact: RemoteArtifact) = ArtifactProvenance(sourceArtifact)

private fun createVcsInfo() =
    VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/apache/logging-log4j2.git",
        revision = "be881e503e14b267fb8a8f94b6d15eddba7ed8c4"
    )

private fun createRepositoryProvenance(
    vcsInfo: VcsInfo = createVcsInfo(),
    resolvedRevision: String = vcsInfo.revision
) = RepositoryProvenance(vcsInfo, resolvedRevision)
