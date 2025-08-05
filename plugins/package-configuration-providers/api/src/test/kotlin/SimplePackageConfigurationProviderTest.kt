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

package org.ossreviewtoolkit.plugins.packageconfigurationproviders.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.VcsMatcher

class SimplePackageConfigurationProviderTest : WordSpec({
    "constructor" should {
        "throw an exception provided multiple package configurations for same Id and source artifact provenance" {
            val packageConfig = PackageConfiguration(
                id = Identifier("type:group:name:version"),
                sourceArtifactUrl = "https://some-host/some/path"
            )
            val configurations = listOf(packageConfig, packageConfig.copy())

            shouldThrow<IllegalArgumentException> {
                SimplePackageConfigurationProvider(configurations = configurations)
            }
        }

        "throw an exception provided multiple package configurations for same Id and VCS provenance" {
            val packageConfig = PackageConfiguration(
                id = Identifier("type:group:name:version"),
                vcs = VcsMatcher(
                    type = VcsType.GIT,
                    url = "https://some-host/some/path.git",
                    revision = "0c1b2aec2812e4833bdca1028b5cb4b6"
                )
            )
            val configurations = listOf(packageConfig, packageConfig.copy())

            shouldThrow<IllegalArgumentException> {
                SimplePackageConfigurationProvider(configurations = configurations)
            }
        }
    }

    "getPackageConfigurations" should {
        val id = Identifier("Maven:org.ossreviewtoolkit:model:1.0.0")
        val provenance = ArtifactProvenance(
            sourceArtifact = RemoteArtifact(url = "https://example.org/artifact.zip", hash = Hash.NONE)
        )

        "return package configurations exactly matching the identifier" {
            val packageConfig1 = PackageConfiguration(id = id, sourceCodeOrigin = SourceCodeOrigin.ARTIFACT)
            val packageConfig2 = PackageConfiguration(id = id, sourceArtifactUrl = provenance.sourceArtifact.url)
            val packageConfig3 =
                PackageConfiguration(id = id.copy(version = "2.0.0"), sourceCodeOrigin = SourceCodeOrigin.ARTIFACT)

            val provider = SimplePackageConfigurationProvider(
                configurations = listOf(packageConfig1, packageConfig2, packageConfig3)
            )

            provider.getPackageConfigurations(id, provenance) should
                containExactlyInAnyOrder(packageConfig1, packageConfig2)
        }

        "return package configurations with matching version ranges" {
            val packageConfig1 =
                PackageConfiguration(id = id.copy(version = "[1.0,2.0)"), sourceCodeOrigin = SourceCodeOrigin.ARTIFACT)
            val packageConfig2 =
                PackageConfiguration(id = id.copy(version = "[0.1,)"), sourceCodeOrigin = SourceCodeOrigin.ARTIFACT)
            val packageConfig3 =
                PackageConfiguration(id = id.copy(version = "]1.0.0,)"), sourceCodeOrigin = SourceCodeOrigin.ARTIFACT)

            val provider = SimplePackageConfigurationProvider(
                configurations = listOf(packageConfig1, packageConfig2, packageConfig3)
            )

            provider.getPackageConfigurations(id, provenance) should
                containExactlyInAnyOrder(packageConfig1, packageConfig2)
        }
    }
})
