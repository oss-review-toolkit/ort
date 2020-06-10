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
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

import io.kotest.matchers.string.haveSubstring
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.WordSpec

import java.io.File

class PubTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects/synthetic/pub/").absoluteFile
    private val projectsDirExternal = File("src/funTest/assets/projects/external/").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Pub" should {
            "resolve dart http dependencies correctly" {
                val workingDir = File(projectsDirExternal, "dart-http")
                val lockFile = File(workingDir, "pubspec.lock")
                File(projectsDirExternal, "dart-http-pubspec.lock").copyTo(lockFile, overwrite = true)

                try {
                    val packageFile = File(workingDir, "pubspec.yaml")
                    val expectedResultFile = File(projectsDirExternal, "dart-http-expected-output.yml")

                    val result = createPubForExternal().resolveSingleProject(packageFile)
                    val vcsPath = vcsDir.getPathToRoot(workingDir)
                    val expectedResult = patchExpectedResult(
                        expectedResultFile,
                        custom = Pair("pub-project", "pub-${workingDir.name}"),
                        definitionFilePath = "$vcsPath/pubspec.yaml",
                        url = normalizeVcsUrl(vcsUrl),
                        revision = vcsRevision,
                        path = vcsPath
                    )

                    yamlMapper.writeValueAsString(result) shouldBe expectedResult
                } finally {
                    lockFile.delete()
                }
            }

            "Resolve dependencies for a project with flutter correctly" {
                val workingDir = File(projectsDir, "project-with-flutter")
                val packageFile = File(workingDir, "pubspec.yaml")
                val expectedResultFile = File(projectsDir.parentFile, "pub-expected-output-project-with-flutter.yml")

                val result = createPub().resolveSingleProject(packageFile)
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    expectedResultFile,
                    custom = Pair("pub-project", "pub-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/pubspec.yaml",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                patchActualResult(yamlMapper.writeValueAsString(result)) shouldBe expectedResult
            }

            "Resolve dependencies for a project with dependencies without a static version" {
                val workingDir = File(projectsDir, "any-version")
                val packageFile = File(workingDir, "pubspec.yaml")
                val expectedResultFile = File(projectsDir.parentFile, "pub-expected-output-any-version.yml")

                val result = createPub().resolveSingleProject(packageFile)
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    expectedResultFile,
                    custom = Pair("pub-project", "pub-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/pubspec.yaml",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "Error is shown when no lockfile is present" {
                val workingDir = File(projectsDir, "no-lockfile")
                val packageFile = File(workingDir, "pubspec.yaml")

                val result = createPub().resolveSingleProject(packageFile)

                with(result) {
                    project.definitionFilePath shouldBe
                            "analyzer/src/funTest/assets/projects/synthetic/pub/no-lockfile/pubspec.yaml"
                    packages.size shouldBe 0
                    issues.size shouldBe 1
                    issues.first().message should haveSubstring("IllegalArgumentException: No lockfile found in")
                }
            }
        }
    }

    private fun createPub() =
        Pub("Pub", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)

    private fun createPubForExternal(): Pub {
        val config = AnalyzerConfiguration(ignoreToolVersions = false, allowDynamicVersions = true)
        return Pub("Pub", USER_DIR, config, DEFAULT_REPOSITORY_CONFIGURATION)
    }
}
