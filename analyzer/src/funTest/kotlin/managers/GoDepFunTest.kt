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
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.haveSubstring

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class GoDepFunTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private val normalizedVcsUrl = normalizeVcsUrl(vcsUrl)
    private val gitHubProject = normalizedVcsUrl.substringAfter("://").substringBefore(".git")

    init {
        "GoDep" should {
            "resolve dependencies from a lockfile correctly" {
                val manifestFile = projectsDir.resolve("synthetic/godep/lockfile/Gopkg.toml")
                val vcsPath = vcsDir.getPathToRoot(manifestFile.parentFile)

                val result = createGoDep().resolveSingleProject(manifestFile)

                val expectedResult = patchExpectedResult(
                    projectsDir.resolve("synthetic/godep-expected-output.yml"),
                    url = normalizedVcsUrl,
                    revision = vcsRevision,
                    path = vcsPath,
                    custom = mapOf("<REPLACE_GITHUB_PROJECT>" to gitHubProject)
                )

                result.toYaml() shouldBe expectedResult
            }

            "show error if no lockfile is present" {
                val manifestFile = projectsDir.resolve("synthetic/godep/no-lockfile/Gopkg.toml")
                val result = createGoDep().resolveSingleProject(manifestFile)

                with(result) {
                    project.id shouldBe
                            Identifier("GoDep::src/funTest/assets/projects/synthetic/godep/no-lockfile/Gopkg.toml:")
                    project.definitionFilePath shouldBe
                            "analyzer/src/funTest/assets/projects/synthetic/godep/no-lockfile/Gopkg.toml"
                    packages.size shouldBe 0
                    issues.size shouldBe 1
                    issues.first().message should haveSubstring("IllegalArgumentException: No lockfile found in")
                }
            }

            "invoke the dependency solver if no lockfile is present and allowDynamicVersions is set" {
                val manifestFile = projectsDir.resolve("synthetic/godep/no-lockfile/Gopkg.toml")
                val config = AnalyzerConfiguration(allowDynamicVersions = true)
                val result = createGoDep(config).resolveSingleProject(manifestFile)

                with(result) {
                    project shouldNotBe Project.EMPTY
                    issues.size shouldBe 0
                }
            }

            "import dependencies from Glide" {
                val manifestFile = projectsDir.resolve("synthetic/godep/glide/glide.yaml")
                val vcsPath = vcsDir.getPathToRoot(manifestFile.parentFile)

                val result = createGoDep().resolveSingleProject(manifestFile)

                val expectedResult = patchExpectedResult(
                    projectsDir.resolve("synthetic/glide-expected-output.yml"),
                    url = normalizedVcsUrl,
                    revision = vcsRevision,
                    path = vcsPath,
                    custom = mapOf("<REPLACE_GITHUB_PROJECT>" to gitHubProject)
                )

                result.toYaml() shouldBe expectedResult
            }

            "import dependencies from godeps" {
                val manifestFile = projectsDir.resolve("synthetic/godep/godeps/Godeps/Godeps.json")
                val vcsPath = vcsDir.getPathToRoot(manifestFile.parentFile.parentFile)

                val result = createGoDep().resolveSingleProject(manifestFile)

                val expectedResult = patchExpectedResult(
                    projectsDir.resolve("synthetic/godeps-expected-output.yml"),
                    url = normalizedVcsUrl,
                    revision = vcsRevision,
                    path = vcsPath,
                    custom = mapOf("<REPLACE_GITHUB_PROJECT>" to gitHubProject)
                )

                result.toYaml() shouldBe expectedResult
            }
        }

        "deduceImportPath()" should {
            val projectDir = projectsDir.resolve("synthetic/godep/lockfile")
            val gopath = File("/tmp/gopath")

            "deduce an import path from VCS info" {
                val vcsInfo = VcsInfo.EMPTY.copy(url = "https://github.com/oss-review-toolkit/ort.git")

                createGoDep().deduceImportPath(projectDir, vcsInfo, gopath) shouldBe
                        gopath.resolve("src/github.com/oss-review-toolkit/ort.git")
            }

            "deduce an import path without VCS info" {
                val vcsInfo = VcsInfo.EMPTY

                createGoDep().deduceImportPath(projectDir, vcsInfo, gopath) shouldBe gopath.resolve("src/lockfile")
            }
        }
    }

    private fun createGoDep(config: AnalyzerConfiguration = DEFAULT_ANALYZER_CONFIGURATION) =
        GoDep("GoDep", USER_DIR, config, DEFAULT_REPOSITORY_CONFIGURATION)
}
