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

package com.here.ort.analyzer.managers

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class CondaTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "conda executable" should {
            "find the environment" {
                val definitionFile = File(projectsDir, "synthetic/python3-django/environment.yml")
                val conda = createConda()

                val envDir = conda.createEnv(definitionFile)

                envDir.path.toLowerCase() shouldContain "conda"
                envDir.name shouldStartWith "ort"
                conda.runInEnv(envDir, "python", "--version").stdout shouldStartWith "Python 3"
                conda.runInEnv(envDir, "pip", "--version").stdout.toLowerCase() shouldContain "conda"
            }
        }

        "Python dependencies" should {
            "resolve dependencies correctly for python-django" {
                val definitionFile = File(projectsDir, "synthetic/python3-django/environment.yml")
                val vcsPath = vcsDir.getPathToRoot(definitionFile.parentFile)

                val result = createConda().resolveDependencies(listOf(definitionFile))[definitionFile]
                val expectedResultFile = File(projectsDir, "synthetic/conda-django-expected-output.yml")
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

    private fun createConda() =
        Conda("Conda", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
