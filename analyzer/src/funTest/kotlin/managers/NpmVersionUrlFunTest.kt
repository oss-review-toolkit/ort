/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class NpmVersionUrlFunTest : WordSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/npm-version-urls").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "NPM" should {
            "resolve dependencies with URLs as versions correctly" {
                val packageFile = projectDir.resolve("package.json")

                val config = AnalyzerConfiguration(allowDynamicVersions = true)
                val result = createNpm(config).resolveSingleProject(packageFile, resolveScopes = true)
                val vcsPath = vcsDir.getPathToRoot(projectDir)
                val expectedResult = patchExpectedResult(
                    projectDir.parentFile.resolve("npm-version-urls-expected-output.yml"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                result.toYaml() shouldBe expectedResult
            }
        }
    }

    private fun createNpm(config: AnalyzerConfiguration = DEFAULT_ANALYZER_CONFIGURATION) =
        Npm("NPM", USER_DIR, config, DEFAULT_REPOSITORY_CONFIGURATION)
}
