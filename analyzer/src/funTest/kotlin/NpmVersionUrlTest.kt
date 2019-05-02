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

import com.here.ort.analyzer.managers.Npm
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class NpmVersionUrlTest : WordSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/npm-version-urls").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    override fun afterTest(testCase: TestCase, result: TestResult) {
        // Make sure the node_modules directory is always deleted from each subdirectory to prevent side-effects
        // from failing tests.
        val nodeModulesDir = projectDir.resolve("node_modules")
        val gitKeepFile = nodeModulesDir.resolve(".gitkeep")
        if (nodeModulesDir.isDirectory && !gitKeepFile.isFile) {
            nodeModulesDir.safeDeleteRecursively(force = true)
        }
    }

    init {
        "NPM" should {
            "resolve dependencies with URLs as versions correctly" {
                val packageFile = File(projectDir, "package.json")

                val config = AnalyzerConfiguration(ignoreToolVersions = false, allowDynamicVersions = true)
                val result = createNPM(config).resolveDependencies(listOf(packageFile))[packageFile]
                val vcsPath = vcsDir.getPathToRoot(projectDir)
                val expectedResult = patchExpectedResult(
                    File(projectDir.parentFile, "npm-version-urls-expected-output.yml"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }
    }

    private fun createNPM(config: AnalyzerConfiguration = DEFAULT_ANALYZER_CONFIGURATION) =
        Npm("NPM", USER_DIR, config, DEFAULT_REPOSITORY_CONFIGURATION)
}
