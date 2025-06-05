/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

private val ARTIFACT_PROVENANCE = ArtifactProvenance(
    sourceArtifact = RemoteArtifact.EMPTY.copy(
        url = "https://host/path/file.zip"
    )
)

private val REPOSITORY_PROVENANCE = RepositoryProvenance(
    vcsInfo = VcsInfo(
        type = VcsType.GIT,
        url = "ssh://git@host/repo.git",
        revision = ""
    ),
    resolvedRevision = "12345678"
)

private fun vcsPackageConfig(name: String, revision: String, url: String) =
    PackageConfiguration(
        id = Identifier.EMPTY.copy(name = name),
        vcs = VcsMatcher(
            type = VcsType.GIT,
            url = url,
            revision = revision
        )
    )

private fun sourceArtifactConfig(name: String, url: String) =
    PackageConfiguration(
        id = Identifier.EMPTY.copy(name = name),
        sourceArtifactUrl = url
    )

class PackageConfigurationTest : WordSpec({
    "matches" should {
        "return true if vcs info and identifier are equal" {
            val config = vcsPackageConfig(name = "some-name", revision = "12345678", url = "ssh://git@host/repo.git")

            config.matches(config.id, REPOSITORY_PROVENANCE) shouldBe true
        }

        "return false if only identifiers are not equal" {
            val config = vcsPackageConfig(name = "some-name", revision = "12345678", url = "ssh://git@host/repo.git")
            val otherId = Identifier.EMPTY.copy(name = "some-other-name")

            config.matches(otherId, REPOSITORY_PROVENANCE) shouldBe false
        }

        "return true if only the VCS URL credentials differ" {
            val config = vcsPackageConfig(name = "some-id", revision = "12345678", url = "ssh://git@host/repo.git")

            config.matches(
                config.id,
                REPOSITORY_PROVENANCE.copy(vcsInfo = REPOSITORY_PROVENANCE.vcsInfo.copy(url = "ssh://host/repo.git"))
            )
        }

        "return false if only resolved revision is not equal to the matcher's revision" {
            val config = vcsPackageConfig(name = "some-name", revision = "12345678", url = "ssh://git@host/repo.git")

            config.matches(config.id, REPOSITORY_PROVENANCE.copy(resolvedRevision = "12")) shouldBe false
        }

        "return true if source artifact URL and identifier are equal" {
            val config = sourceArtifactConfig(name = "some-name", url = "https://host/path/file.zip")

            config.matches(config.id, ARTIFACT_PROVENANCE) shouldBe true
        }

        "return false if only the source artifact URL is not equal" {
            val config = sourceArtifactConfig(name = "some-name", url = "https://host/path/some-file.zip")

            config.matches(
                config.id,
                ARTIFACT_PROVENANCE.copy(
                    sourceArtifact = ARTIFACT_PROVENANCE.sourceArtifact.copy(
                        url = "https://host/path/some-other-file.zip"
                    )
                )
            ) shouldBe false
        }

        "return true if the identifier type is equal ignoring case" {
            val config = vcsPackageConfig(name = "some-name", revision = "12345678", url = "ssh://git@host/repo.git")
                .let { it.copy(id = it.id.copy(type = "Gradle")) }

            config.matches(config.id.copy(type = "gradle"), REPOSITORY_PROVENANCE) shouldBe true
        }

        "return true if vcs source code origin and identifier are equal" {
            val config = PackageConfiguration(
                id = Identifier.EMPTY.copy(name = "some-name"),
                sourceCodeOrigin = SourceCodeOrigin.VCS
            )

            config.matches(config.id, REPOSITORY_PROVENANCE) shouldBe true
        }

        "return true if artifact source code origin and identifier are equal" {
            val config = PackageConfiguration(
                id = Identifier.EMPTY.copy(name = "some-name"),
                sourceCodeOrigin = SourceCodeOrigin.ARTIFACT
            )

            config.matches(config.id, ARTIFACT_PROVENANCE) shouldBe true
        }

        "return true if source code origin is equal and identifier matches with version is in range" {
            val config = PackageConfiguration(
                id = Identifier.EMPTY.copy(name = "some-name", version = "[51.0.0,60.0.0]"),
                sourceCodeOrigin = SourceCodeOrigin.ARTIFACT
            )

            config.matches(config.id.copy(version = "55"), ARTIFACT_PROVENANCE) shouldBe true
        }

        "return false if only source code origin is not equal" {
            val config = PackageConfiguration(
                id = Identifier.EMPTY.copy(name = "some-name"),
                sourceCodeOrigin = SourceCodeOrigin.ARTIFACT
            )

            config.matches(config.id, REPOSITORY_PROVENANCE) shouldBe false
        }

        "return true if the package configuration contains only the identifier and the latter matches" {
            val config = PackageConfiguration(id = Identifier.EMPTY.copy(name = "some-name"))

            config.matches(config.id, REPOSITORY_PROVENANCE) shouldBe true
        }

        "return false if only the source code origin is not equal" {
            val config = PackageConfiguration(
                id = Identifier.EMPTY.copy(name = "some-name"),
                sourceCodeOrigin = SourceCodeOrigin.VCS
            )

            config.matches(config.id, ARTIFACT_PROVENANCE) shouldBe false
        }

        "return true if only matched by id with a matching version range" {
            val config = PackageConfiguration(
                id = Identifier.EMPTY.copy(name = "some-name", version = "[51.0.0,60.0.0]")
            )

            config.matches(config.id.copy(version = "55"), ARTIFACT_PROVENANCE) shouldBe true
        }

        "return false if only matched by id with a non-matching version range" {
            val config = PackageConfiguration(
                id = Identifier.EMPTY.copy(name = "some-name", version = "[51.0.0,60.0.0]")
            )

            config.matches(config.id.copy(version = "6"), ARTIFACT_PROVENANCE) shouldBe false
        }
    }

    "init()" should {
        "throw if a version range is given while having a vcs" {
            shouldThrow<IllegalArgumentException> {
                PackageConfiguration(
                    id = Identifier.EMPTY.copy(version = "[51.0.0,60.0.0]"),
                    vcs = VcsMatcher(
                        type = VcsType.GIT,
                        revision = "12345678",
                        url = "ssh://git@host/repo.git"
                    )
                )
            }
        }

        "throw if a version range is given while having a source artifact URL" {
            shouldThrow<IllegalArgumentException> {
                PackageConfiguration(
                    id = Identifier.EMPTY.copy(version = "[51.0.0,60.0.0]"),
                    sourceArtifactUrl = "https://host/path/file.zip"
                )
            }
        }

        "not throw if a version range with a range indicator is given while having a source artifact URL" {
            PackageConfiguration(
                id = Identifier.EMPTY.copy(version = "0.13.3+wasi-0.2.2"),
                sourceArtifactUrl = "https://host/path/file.zip"
            )
        }
    }
})
