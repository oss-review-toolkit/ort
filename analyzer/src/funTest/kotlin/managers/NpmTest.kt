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

package org.ossreviewtoolkit.analyzer.managers

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class NpmTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects/synthetic/npm").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "NPM" should {
            "resolve shrinkwrap dependencies correctly" {
                val workingDir = File(projectsDir, "shrinkwrap")
                val packageFile = File(workingDir, "package.json")

                val result = createNPM().resolveDependencies(listOf(packageFile))[packageFile]
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    File(projectsDir.parentFile, "npm-expected-output.yml"),
                    custom = Pair("npm-project", "npm-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "resolve package-lock dependencies correctly" {
                val workingDir = File(projectsDir, "package-lock")
                val packageFile = File(workingDir, "package.json")

                val result = createNPM().resolveDependencies(listOf(packageFile))[packageFile]
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    File(projectsDir.parentFile, "npm-expected-output.yml"),
                    custom = Pair("npm-project", "npm-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "show error if no lockfile is present" {
                val workingDir = File(projectsDir, "no-lockfile")
                val packageFile = File(workingDir, "package.json")

                val result = createNPM().resolveDependencies(listOf(packageFile))[packageFile]
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    File(projectsDir.parentFile, "npm-expected-output-no-lockfile.yml"),
                    custom = Pair("npm-project", "npm-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                patchActualResult(yamlMapper.writeValueAsString(result)) shouldBe expectedResult
            }

            "resolve dependencies even if the node_modules directory already exists" {
                val workingDir = File(projectsDir, "node-modules")
                val packageFile = File(workingDir, "package.json")

                val result = createNPM().resolveDependencies(listOf(packageFile))[packageFile]
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    File(projectsDir.parentFile, "npm-expected-output.yml"),
                    custom = Pair("npm-project", "npm-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }
    }

    private fun createNPM() =
        Npm("NPM", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
