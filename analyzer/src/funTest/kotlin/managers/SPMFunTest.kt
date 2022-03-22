/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class SPMFunTest : WordSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/spm").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private val normalizedVcsUrl = normalizeVcsUrl(vcsUrl)
    private val gitHubProject = normalizedVcsUrl.split('/', '.').dropLast(1).takeLast(2).joinToString(":")

    init {
        "SPM" should {
            "Parse Package.resolved dependencies correctly" {
                createSPM(DEFAULT_ANALYZER_CONFIGURATION)
                    .resolveSingleProject(projectDir.resolve("Package.resolved"))
                    .toYaml() shouldBe patchExpectedResult(
                    definitionFileName = "Package.resolved",
                    expectedFilename = "spm-expected-output-app.yml"
                )
            }

            "Parse Package.swift dependencies correctly" {
                createSPM(AnalyzerConfiguration(allowDynamicVersions = true))
                    .resolveSingleProject(projectDir.resolve("Package.swift"), resolveScopes = true)
                    .toYaml() shouldBe patchExpectedResult(
                    definitionFileName = "Package.swift",
                    expectedFilename = "spm-expected-output-lib.yml"
                )
            }

            "Show error if only Package.swift is present but allowDynamicVersions is set to false" {
                val actualResult = createSPM(DEFAULT_ANALYZER_CONFIGURATION)
                    .resolveSingleProject(projectDir.resolve("Package.swift"), resolveScopes = true)
                    .toYaml()
                patchActualResult(actualResult, patchStartAndEndTime = true) shouldBe patchExpectedResult(
                    definitionFileName = "Package.swift",
                    expectedFilename = "spm-expected-output-no-lockfile.yml"
                )
            }
        }
    }

    private inner class MockSPMCLIExecutor : SPMCLIExecutor {
        override fun executeSwift(definitionFile: File): JsonNode {
            val mockedDependencies = projectDir.resolve("spm-package-show-dependencies.json")
            return ObjectMapper().readValue(mockedDependencies, JsonNode::class.java)
        }
    }

    private fun patchExpectedResult(definitionFileName: String, expectedFilename: String): String {
        val vcsPath = vcsDir.getPathToRoot(projectDir)
        return patchExpectedResult(
            path = vcsPath,
            revision = vcsRevision,
            url = normalizedVcsUrl,
            definitionFilePath = "$vcsPath/$definitionFileName",
            custom = mapOf("<REPLACE_GITHUB_PROJECT>" to gitHubProject),
            result = projectDir.parentFile.resolve(expectedFilename),
        )
    }

    private fun createSPM(analyzerConfiguration: AnalyzerConfiguration) = SPM(
        name = "SPM",
        analysisRoot = USER_DIR,
        cliExecutor = MockSPMCLIExecutor(),
        analyzerConfig = analyzerConfiguration,
        repoConfig = DEFAULT_REPOSITORY_CONFIGURATION
    )
}
