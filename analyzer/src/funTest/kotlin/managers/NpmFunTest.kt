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
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class NpmFunTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects/synthetic/npm").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "NPM" should {
            "resolve shrinkwrap dependencies correctly" {
                val workingDir = projectsDir.resolve("shrinkwrap")
                val packageFile = workingDir.resolve("package.json")

                val result = createNpm().resolveSingleProject(packageFile, resolveScopes = true)
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    projectsDir.parentFile.resolve("npm-expected-output.yml"),
                    custom = mapOf("npm-project" to "npm-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                result.toYaml() shouldBe expectedResult
            }

            "resolve package-lock dependencies correctly" {
                val workingDir = projectsDir.resolve("package-lock")
                val packageFile = workingDir.resolve("package.json")

                val result = createNpm().resolveSingleProject(packageFile, resolveScopes = true)
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    projectsDir.parentFile.resolve("npm-expected-output.yml"),
                    custom = mapOf("npm-project" to "npm-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                result.toYaml() shouldBe expectedResult
            }

            "show error if no lockfile is present" {
                val workingDir = projectsDir.resolve("no-lockfile")
                val packageFile = workingDir.resolve("package.json")

                val result = createNpm().resolveSingleProject(packageFile)
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    projectsDir.parentFile.resolve("npm-expected-output-no-lockfile.yml"),
                    custom = mapOf("npm-project" to "npm-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                patchActualResult(result.toYaml()) shouldBe expectedResult
            }

            "resolve dependencies even if the node_modules directory already exists" {
                val workingDir = projectsDir.resolve("node-modules")
                val packageFile = workingDir.resolve("package.json")

                val result = createNpm().resolveSingleProject(packageFile, resolveScopes = true)
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    projectsDir.parentFile.resolve("npm-expected-output.yml"),
                    custom = mapOf("npm-project" to "npm-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                result.toYaml() shouldBe expectedResult
            }
        }
    }

    private fun createNpm() =
        Npm("NPM", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
