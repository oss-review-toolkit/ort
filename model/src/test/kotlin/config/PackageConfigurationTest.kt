/*
 * Copyright (C) 2020 HERE Europe B.V.
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

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

            config.matches(
                config.id,
                RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        url = "ssh://git@host/repo.git",
                        revision = ""
                    ),
                    resolvedRevision = "12345678"
                )
            ) shouldBe true
        }

        "return false if only identifiers are not equal" {
            val config = vcsPackageConfig(name = "some-name", revision = "12345678", url = "ssh://git@host/repo.git")

            config.matches(
                Identifier.EMPTY.copy(name = "some-other-name"),
                RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        url = "ssh://git@host/repo.git",
                        revision = ""
                    ),
                    resolvedRevision = "12345678"
                )
            ) shouldBe false
        }

        "return true if only the VCS URL credentials differ" {
            val config = vcsPackageConfig(name = "some-id", revision = "12345678", url = "ssh://git@host/repo.git")

            config.matches(
                config.id,
                RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        url = "ssh://host/repo.git",
                        revision = ""
                    ),
                    resolvedRevision = "12345678"
                )
            ) shouldBe true
        }

        "return false if only resolved revision is not equal to the matcher's revision" {
            val config = vcsPackageConfig(name = "some-name", revision = "12345678", url = "ssh://git@host/repo.git")

            config.matches(
                config.id,
                RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        url = "ssh://git@host/repo.git",
                        revision = "12345678"
                    ),
                    resolvedRevision = "12"
                )
            ) shouldBe false
        }

        "return true if source artifact URL and identifier are equal" {
            val config = sourceArtifactConfig(name = "some-name", url = "https://host/path/file.zip")

            config.matches(
                config.id,
                ArtifactProvenance(
                    sourceArtifact = RemoteArtifact.EMPTY.copy(
                        url = "https://host/path/file.zip"
                    )
                )
            ) shouldBe true
        }

        "return false if only the source artifact URL is not equal" {
            val config = sourceArtifactConfig(name = "some-name", url = "https://host/path/some-file.zip")

            config.matches(
                config.id,
                ArtifactProvenance(
                    sourceArtifact = RemoteArtifact.EMPTY.copy(
                        url = "https://host/path/some-other-file.zip"
                    )
                )
            ) shouldBe false
        }
    }
})
