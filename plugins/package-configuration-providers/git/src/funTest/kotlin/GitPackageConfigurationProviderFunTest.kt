/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packageconfigurationproviders.git

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.plugins.versioncontrolsystems.git.GitFactory
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.ort.ORT_DATA_DIR_ENV_NAME

/** A fixed revision to ensure that the test is not affected by changes to the repository. */
private const val REVISION = "f7f6970433f3eb908da514fc118c109f8f119b3e"

@Tags("RequiresExternalTool")
class GitPackageConfigurationProviderFunTest : WordSpec({
    Os.env[ORT_DATA_DIR_ENV_NAME] = tempdir().absolutePath

    "create()" should {
        "clone the correct revision" {
            val provider = GitPackageConfigurationProviderFactory.create(
                repositoryUrl = ORT_CONFIG_REPOSITORY_URL,
                revision = REVISION
            )

            val workingTree = GitFactory.create().getWorkingTree(provider.repositoryDir)

            workingTree.getRevision() shouldBe REVISION
        }

        "clone the default branch if no revision is provided" {
            val provider = GitPackageConfigurationProviderFactory.create(ORT_CONFIG_REPOSITORY_URL)

            val git = GitFactory.create()
            val workingTree = git.getWorkingTree(provider.repositoryDir)
            val clonedRevision = workingTree.getRevision()

            git.updateWorkingTree(workingTree, "main")

            clonedRevision shouldBe workingTree.getRevision()
        }
    }

    "getPackageConfigurations()" should {
        val provider = GitPackageConfigurationProviderFactory.create(
            repositoryUrl = ORT_CONFIG_REPOSITORY_URL,
            revision = REVISION,
            path = "package-configurations"
        )

        "return the package configurations for a repository provenance" {
            val result = provider.getPackageConfigurations(
                packageId = Identifier("Maven:org.apache.commons:commons-compress:1.26.2"),
                provenance = RepositoryProvenance(
                    vcsInfo = VcsInfo(
                        type = VcsType.GIT,
                        url = "https://gitbox.apache.org/repos/asf/commons-compress.git",
                        revision = "95727006cac0892c654951c4e7f1db142462f22a"
                    ),
                    resolvedRevision = "95727006cac0892c654951c4e7f1db142462f22a"
                )
            )

            result.shouldBeSingleton {
                it.id shouldBe Identifier("Maven:org.apache.commons:commons-compress:1.26.2")
                it.pathExcludes should containExactly(
                    PathExclude(
                        pattern = "src/test/**",
                        reason = PathExcludeReason.TEST_OF
                    )
                )
            }
        }

        "return the package configuration for an artifact provenance" {
            val result = provider.getPackageConfigurations(
                packageId = Identifier("PyPI::flask:1.1.2"),
                provenance = ArtifactProvenance(
                    sourceArtifact = RemoteArtifact.EMPTY.copy(
                        url = "https://files.pythonhosted.org/packages/4e/0b/" +
                            "cb02268c90e67545a0e3a37ea1ca3d45de3aca43ceb7dbf1712fb5127d5d/Flask-1.1.2.tar.gz"
                    )
                )
            )

            result.shouldBeSingleton {
                it.id shouldBe Identifier("PyPI::flask:1.1.2")
                it.pathExcludes should containExactly(
                    PathExclude(
                        pattern = "Flask-1.1.2/artwork/**",
                        reason = PathExcludeReason.DATA_FILE_OF
                    ),
                    PathExclude(
                        pattern = "Flask-1.1.2/docs/**",
                        reason = PathExcludeReason.DOCUMENTATION_OF
                    ),
                    PathExclude(
                        pattern = "Flask-1.1.2/examples/**",
                        reason = PathExcludeReason.EXAMPLE_OF
                    ),
                    PathExclude(
                        pattern = "Flask-1.1.2/tests/**",
                        reason = PathExcludeReason.TEST_OF
                    )
                )
            }
        }

        "return an empty result for a package which has no configurations" {
            val result = provider.getPackageConfigurations(
                packageId = Identifier("PyPI::flask:1.1.3"),
                provenance = ArtifactProvenance(
                    RemoteArtifact.EMPTY.copy(
                        url = "https://files.pythonhosted.org/packages/eb/29/" +
                            "d5afa1447df7e4ccb4d7f9427de5beef2210d238c0fefbef4bbe165d2f50/Flask-1.1.3.tar.gz"
                    )
                )
            )

            result should beEmpty()
        }
    }
})
