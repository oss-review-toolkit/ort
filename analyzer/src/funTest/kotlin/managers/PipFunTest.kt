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
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class PipFunTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Python 2" should {
            "resolve setup.py dependencies correctly for spdx-tools-python" {
                val definitionFile = projectsDir.resolve("external/spdx-tools-python/setup.py")

                val result = createPip().resolveSingleProject(definitionFile)
                val expectedResult = projectsDir.resolve("external/spdx-tools-python-expected-output.yml").readText()

                result.toYaml() shouldBe expectedResult
            }

            "capture metadata from setup.py even if requirements.txt is present" {
                val definitionFile = projectsDir.resolve("synthetic/pip/requirements.txt")
                val vcsPath = vcsDir.getPathToRoot(definitionFile.parentFile)

                val expectedResult = patchExpectedResult(
                    projectsDir.resolve("synthetic/pip-expected-output.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                val result = createPip().resolveSingleProject(definitionFile)

                result.toYaml() shouldBe expectedResult
            }
        }

        "Python 3" should {
            "resolve requirements.txt dependencies correctly for example-python-flask".config(enabled = !Os.isWindows) {
                val definitionFile = projectsDir.resolve("external/example-python-flask/requirements.txt")

                val result = createPip().resolveSingleProject(definitionFile)

                // Note: The expected results were generated with Python 3.8 and are incorrect for versions < 3.8.
                val expectedResult = projectsDir.resolve("external/example-python-flask-expected-output.yml").readText()

                result.toYaml() shouldBe expectedResult
            }

            "resolve dependencies correctly for a Django project" {
                val definitionFile = projectsDir.resolve("synthetic/pip-python3/requirements.txt")
                val vcsPath = vcsDir.getPathToRoot(definitionFile.parentFile)

                val result = createPip().resolveSingleProject(definitionFile)
                val expectedResultFile = projectsDir.resolve("synthetic/pip-python3-expected-output.yml")
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

    private fun createPip() =
        Pip("PIP", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
