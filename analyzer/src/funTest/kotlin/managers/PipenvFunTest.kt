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
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class PipenvFunTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects").absoluteFile
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
        Pipenv("Pipenv", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
