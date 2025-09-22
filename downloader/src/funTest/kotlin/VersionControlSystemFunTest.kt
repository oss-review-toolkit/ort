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

package org.ossreviewtoolkit.downloader

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe

import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.PluginConfig

@Tags("RequiresExternalTool")
class VersionControlSystemFunTest : WordSpec({
    "forUrl()" should {
        "return null for an unsupported repository URL" {
            val repositoryUrl = "https://example.com"

            val vcs = VersionControlSystem.forUrl(repositoryUrl)

            vcs should beNull()
        }

        "return a VCS instance that can handle a Git repository URL" {
            val repositoryUrl = "https://github.com/oss-review-toolkit/ort.git"

            val vcs = VersionControlSystem.forUrl(repositoryUrl)

            vcs shouldNotBeNull {
                type shouldBe VcsType.GIT
            }
        }

        "return the VCS instance with the correct configuration from cache" {
            val repositoryUrl = "https://github.com/oss-review-toolkit/ort.git"
            val configs = mapOf(
                VcsType.GIT.toString() to PluginConfig(
                    options = mapOf("updateNestedSubmodules" to false.toString())
                )
            )

            val vcsWithDefaultConfiguration = VersionControlSystem.forUrl(repositoryUrl)
            val vcsWithConfigs = VersionControlSystem.forUrl(repositoryUrl, configs)
            val vcsWithConfigsFromCache = VersionControlSystem.forUrl(repositoryUrl, configs)

            vcsWithDefaultConfiguration shouldNot beNull()
            vcsWithConfigs shouldNot beNull()

            vcsWithDefaultConfiguration shouldNotBe vcsWithConfigs
            vcsWithConfigsFromCache shouldBe vcsWithConfigs
        }
    }
})
