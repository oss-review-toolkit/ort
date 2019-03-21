/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.analyzer

import com.here.ort.analyzer.managers.PIP
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class PipTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects")
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Python 2" should {
            "resolve setup.py dependencies correctly for spdx-tools-python" {
                val definitionFile = File(projectsDir, "external/spdx-tools-python/setup.py")

                val result = createPIP().resolveDependencies(USER_DIR, listOf(definitionFile))[definitionFile]
                val expectedResult = File(projectsDir, "external/spdx-tools-python-expected-output.yml").readText()

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "resolve requirements.txt dependencies correctly for example-python-flask" {
                val definitionFile = File(projectsDir, "external/example-python-flask/requirements.txt")

                val result = createPIP().resolveDependencies(USER_DIR, listOf(definitionFile))[definitionFile]
                val expectedResult = File(projectsDir, "external/example-python-flask-expected-output.yml").readText()

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "capture metadata from setup.py even if requirements.txt is present" {
                val definitionFile = File(projectsDir, "synthetic/pip/requirements.txt")
                val vcsPath = vcsDir.getPathToRoot(definitionFile.parentFile)

                val expectedResult = patchExpectedResult(
                    File(projectsDir, "synthetic/pip-expected-output.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                val result = createPIP().resolveDependencies(USER_DIR, listOf(definitionFile))[definitionFile]

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }

        "Python 3" should {
            "resolve dependencies correctly for python-django" {
                val definitionFile = File(projectsDir, "synthetic/python3-django/requirements.txt")
                val vcsPath = vcsDir.getPathToRoot(definitionFile.parentFile)

                val result = createPIP().resolveDependencies(USER_DIR, listOf(definitionFile))[definitionFile]
                val expectedResultFile = File(projectsDir, "synthetic/python3-django-expected-output.yml")
                val expectedResult = patchExpectedResult(
                    expectedResultFile,
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }
    }

    private fun createPIP() = PIP("PIP", DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
