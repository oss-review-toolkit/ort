/*
 * Copyright (C) 2019-2020 HERE Europe B.V.
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
import com.here.ort.utils.log
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.patchActualResult
import com.here.ort.utils.test.patchExpectedResult
// import com.here.ort.utils.test.patchActualResult

import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class PnpmTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects/synthetic/pnpm").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    override fun afterTest(testCase: TestCase, result: TestResult) {
        // Make sure the node_modules directory is alpatchAways deleted from each subdirectory to prevent side-effects
        // from failing tests.
        projectsDir.listFiles()?.forEach {
            if (!it.isDirectory) return@forEach

            val nodeModulesDir = File(it, "node_modules")

            if (!nodeModulesDir.isDirectory) return@forEach
            if (File(nodeModulesDir, ".gitkeep").isFile) return@forEach

            try {
                nodeModulesDir.safeDeleteRecursively(force = true)
            } catch (e: java.nio.file.NoSuchFileException) {
                log.info {
                    "Failed to delete $nodeModulesDir"
                }
            }
        }
    }

    init {
        "PNPM" should {
            "resolve pnpm-lock dependencies correctly" {
                val workingDir = File(projectsDir, "pnpm-lock")
                val packageFile = File(workingDir, "package.json")

                // val expectedOutput = createPNPM().resolveDependencies(listOf(packageFile))[packageFile]
                // yamlMapper.writeValue(File("$workingDir/pnpm-expected-output.yml"), expectedOutput)

                val result = createPNPM().resolveDependencies(listOf(packageFile))[packageFile]
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    File(projectsDir.parentFile, "pnpm-expected-output.yml"),
                    custom = Pair("pnpm-project", "pnpm-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            // "resolve dependencies even if the node_modules directory already exists" {
            //     val workingDir = File(projectsDir, "node-modules")
            //     val packageFile = File(workingDir, "package.json")
            //
            //     // val expectedOutput = createPNPM().resolveDependencies(listOf(packageFile))[packageFile]
            //     // yamlMapper.writeValue(File("$workingDir/pnpm-node-modules-expected-output.yml"), expectedOutput)
            //
            //     val result = createPNPM().resolveDependencies(listOf(packageFile))[packageFile]
            //     val vcsPath = vcsDir.getPathToRoot(workingDir)
            //     val expectedResult = patchExpectedResult(
            //         File(projectsDir.parentFile, "pnpm-expected-output.yml"),
            //         custom = Pair("pnpm-project", "pnpm-${workingDir.name}"),
            //         definitionFilePath = "$vcsPath/package.json",
            //         url = normalizeVcsUrl(vcsUrl),
            //         revision = vcsRevision,
            //         path = vcsPath
            //     )
            //
            //     yamlMapper.writeValueAsString(result) shouldBe expectedResult
            // }

            "show error if no pnpm lockfile is present" {
                val workingDir = File(projectsDir, "no-lockfile")
                val packageFile = File(workingDir, "package.json")

                // val expectedOutput = createPNPM().resolveDependencies(listOf(packageFile))[packageFile]
                // yamlMapper.writeValue(File("$workingDir/pnpm-no-lockfile-expected-output.yml"), expectedOutput)

                val result = createPNPM().resolveDependencies(listOf(packageFile))[packageFile]
                val vcsPath = vcsDir.getPathToRoot(workingDir)
                val expectedResult = patchExpectedResult(
                    File(projectsDir.parentFile, "pnpm-no-lockfile-expected-output.yml"),
                    custom = Pair("pnpm-project", "pnpm-${workingDir.name}"),
                    definitionFilePath = "$vcsPath/package.json",
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsPath
                )

                patchActualResult(yamlMapper.writeValueAsString(result)) shouldBe expectedResult
            }
        }
    }

    private fun createPNPM() =
        Pnpm("PNPM", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
