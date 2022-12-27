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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.createTestTempDir
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
                val definitionFile = workingDir.resolve("package.json")

                val result = createNpm().resolveSingleProject(definitionFile, resolveScopes = true)
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    projectsDir.resolveSibling("npm-expected-output.yml"),
                    custom = mapOf(
                        "npm-project" to "npm-${workingDir.name}",
                        "<REPLACE_LOCKFILE_NAME>" to "npm-shrinkwrap.json"
                    ),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                patchActualResult(result.toYaml()) shouldBe expectedResult
            }

            "resolve package-lock dependencies correctly" {
                val workingDir = projectsDir.resolve("package-lock")
                val definitionFile = workingDir.resolve("package.json")

                val result = createNpm().resolveSingleProject(definitionFile, resolveScopes = true)
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    projectsDir.resolveSibling("npm-expected-output.yml"),
                    custom = mapOf(
                        "npm-project" to "npm-${workingDir.name}",
                        "<REPLACE_LOCKFILE_NAME>" to "package-lock.json"
                    ),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                patchActualResult(
                    result.toYaml(),
                    // Deal with wrapping depending on the lockfile name length.
                    custom = mapOf("so\\\n    \\ supplemental metadata" to "\\\n    so supplemental metadata")
                ) shouldBe expectedResult
            }

            "show an error if no lockfile is present" {
                val workingDir = projectsDir.resolve("no-lockfile")
                val definitionFile = workingDir.resolve("package.json")

                val result = createNpm().resolveSingleProject(definitionFile)
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    projectsDir.resolveSibling("npm-expected-output-no-lockfile.yml"),
                    custom = mapOf("npm-project" to "npm-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                patchActualResult(result.toYaml()) shouldBe expectedResult
            }

            "show an error if the 'package.json' file is invalid" {
                val workingDir = createTestTempDir()
                val definitionFile = workingDir.resolve("package.json").apply { writeText("<>") }

                val result = createNpm(allowDynamicVersions = true).resolveSingleProject(definitionFile)

                result.issues shouldHaveSize 1
                with(result.issues.first()) {
                    source shouldBe "NPM"
                    severity shouldBe Severity.ERROR
                    message shouldContain "Unexpected token \"<\" (0x3C) in JSON at position 0 while parsing \"<>\""
                }
            }

            "resolve dependencies even if the 'node_modules' directory already exists" {
                val workingDir = projectsDir.resolve("node-modules")
                val definitionFile = workingDir.resolve("package.json")

                val result = createNpm().resolveSingleProject(definitionFile, resolveScopes = true)
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    projectsDir.resolveSibling("npm-expected-output.yml"),
                    custom = mapOf(
                        "npm-project" to "npm-${workingDir.name}",
                        "<REPLACE_LOCKFILE_NAME>" to "package-lock.json"
                    ),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                patchActualResult(
                    result.toYaml(),
                    // Deal with wrapping depending on the lockfile name length.
                    custom = mapOf("so\\\n    \\ supplemental metadata" to "\\\n    so supplemental metadata")
                ) shouldBe expectedResult
            }
        }
    }

    private fun createNpm(allowDynamicVersions: Boolean = false) =
        Npm("NPM", USER_DIR, AnalyzerConfiguration(allowDynamicVersions), RepositoryConfiguration())
}
