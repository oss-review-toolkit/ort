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
import io.kotest.matchers.string.haveSubstring

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class BundlerFunTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects/synthetic/bundler").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsRevision = vcsDir.getRevision()
    private val vcsUrl = vcsDir.getRemoteUrl()

    init {
        "Bundler" should {
            "resolve dependencies correctly" {
                val definitionFile = projectsDir.resolve("lockfile/Gemfile")

                try {
                    val actualResult = createBundler().resolveSingleProject(definitionFile)
                    val expectedResult = patchExpectedResult(
                        projectsDir.parentFile.resolve("bundler-expected-output-lockfile.yml"),
                        url = normalizeVcsUrl(vcsUrl),
                        revision = vcsRevision,
                        path = vcsDir.getPathToRoot(definitionFile.parentFile)
                    )

                    actualResult.toYaml() shouldBe expectedResult
                } finally {
                    File(definitionFile.parentFile, ".bundle").safeDeleteRecursively(force = true)
                }
            }

            "show error if no lockfile is present" {
                val definitionFile = projectsDir.resolve("no-lockfile/Gemfile")
                val actualResult = createBundler().resolveSingleProject(definitionFile)

                with(actualResult) {
                    project.id shouldBe
                            Identifier("Bundler::src/funTest/assets/projects/synthetic/bundler/no-lockfile/Gemfile:")
                    project.definitionFilePath shouldBe
                            "analyzer/src/funTest/assets/projects/synthetic/bundler/no-lockfile/Gemfile"
                    packages.size shouldBe 0
                    issues.size shouldBe 1
                    issues.first().message should haveSubstring("IllegalArgumentException: No lockfile found in")
                }
            }

            "resolve dependencies correctly when the project is a Gem" {
                val definitionFile = projectsDir.resolve("gemspec/Gemfile")

                try {
                    val actualResult = createBundler().resolveSingleProject(definitionFile)
                    val expectedResult = patchExpectedResult(
                        projectsDir.parentFile.resolve("bundler-expected-output-gemspec.yml"),
                        url = normalizeVcsUrl(vcsUrl),
                        revision = vcsRevision,
                        path = vcsDir.getPathToRoot(definitionFile.parentFile)
                    )

                    actualResult.toYaml() shouldBe expectedResult
                } finally {
                    File(definitionFile.parentFile, ".bundle").safeDeleteRecursively(force = true)
                }
            }
        }
    }

    private fun createBundler() =
        Bundler("Bundler", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
