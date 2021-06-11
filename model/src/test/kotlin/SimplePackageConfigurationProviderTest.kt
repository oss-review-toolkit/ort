/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec

import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.VcsMatcher
import org.ossreviewtoolkit.model.utils.SimplePackageConfigurationProvider

class SimplePackageConfigurationProviderTest : WordSpec({
    "constructor" should {
        "throw an exception provided multiple package configurations for same Id and source artifact provenance" {
            val packageConfig = PackageConfiguration(
                id = Identifier("type:group:name:version"),
                sourceArtifactUrl = "https://some-host/some/path"
            )
            val configurations = listOf(packageConfig, packageConfig.copy())

            shouldThrow<IllegalArgumentException> {
                SimplePackageConfigurationProvider(configurations)
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
                SimplePackageConfigurationProvider(configurations)
            }
        }
    }
})
