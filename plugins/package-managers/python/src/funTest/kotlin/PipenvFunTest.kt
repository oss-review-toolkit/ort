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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.managers.resolveSingleProject
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.toYaml

class PipenvFunTest : WordSpec() {
    private val projectsDir = getAssetFile("projects")
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Python 2" should {
            "resolve dependencies correctly" {
                val definitionFile = projectsDir.resolve("synthetic/pipenv/Pipfile.lock")
                val vcsPath = vcsDir.getPathToRoot(definitionFile.parentFile)

                val expectedResult = patchExpectedResult(
                    projectsDir.resolve("synthetic/pipenv-expected-output.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                val result = createPipenv().resolveSingleProject(definitionFile)

                result.toYaml() shouldBe expectedResult
            }
        }

        "Python 3" should {
            "resolve dependencies correctly for a Django project" {
                val definitionFile = projectsDir.resolve("synthetic/pipenv-python3/Pipfile.lock")
                val vcsPath = vcsDir.getPathToRoot(definitionFile.parentFile)

                val result = createPipenv().resolveSingleProject(definitionFile)
                val expectedResultFile = projectsDir.resolve("synthetic/pipenv-python3-expected-output.yml")
                val expectedResult = patchExpectedResult(
                    expectedResultFile,
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                result.toYaml() shouldBe expectedResult
            }
        }
    }

    private fun createPipenv() =
        Pipenv("Pipenv", USER_DIR, AnalyzerConfiguration(), RepositoryConfiguration())
}
