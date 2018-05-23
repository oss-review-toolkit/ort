/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.analyzer.managers.NPM
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.searchUpwardsForSubdirectory

import io.kotlintest.Description
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec

import java.io.File

class NpmTest : FreeSpec() {
    private val rootDir = File(".").searchUpwardsForSubdirectory(".git")!!
    private val projectDir = File(rootDir, "analyzer/src/funTest/assets/projects/synthetic/npm")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    override fun afterTest(description: Description, result: TestResult) {
        // Make sure the node_modules directory is always deleted from each subdirectory to prevent side-effects
        // from failing tests.
        projectDir.listFiles().forEach {
            if (it.isDirectory) {
                val nodeModulesDir = File(it, "node_modules")
                val gitKeepFile = File(nodeModulesDir, ".gitkeep")
                if (nodeModulesDir.isDirectory && !gitKeepFile.isFile) {
                    nodeModulesDir.safeDeleteRecursively()
                }
            }
        }
    }

    private fun patchExpectedResult(workingDir: File, expectedResultFile: String = "npm-expected-output.yml"): String {
        val vcsPath = workingDir.relativeTo(rootDir).invariantSeparatorsPath
        return File(projectDir.parentFile, expectedResultFile).readText()
                // project.name:
                .replaceFirst("npm-project", "npm-${workingDir.name}")
                // project.definitionFilePath
                .replaceFirst("<REPLACE_DEFINITION_FILE_PATH>", "$vcsPath/package.json")
                // project.vcs_processed:
                .replaceFirst("<REPLACE_URL>", normalizeVcsUrl(vcsUrl))
                .replaceFirst("<REPLACE_REVISION>", vcsRevision)
                .replaceFirst("<REPLACE_PATH>", vcsPath)
    }

    init {
        "NPM should" - {
            "resolve shrinkwrap dependencies correctly" {
                val workingDir = File(projectDir, "shrinkwrap")
                val packageFile = File(workingDir, "package.json")
                val npm = NPM.create()

                val result = npm.resolveDependencies(listOf(packageFile))[packageFile]
                val expectedResult = patchExpectedResult(workingDir)

                npm.command(workingDir) shouldBe NPM.npm
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "resolve package-lock dependencies correctly" {
                val workingDir = File(projectDir, "package-lock")
                val packageFile = File(workingDir, "package.json")
                val npm = NPM.create()

                val result = npm.resolveDependencies(listOf(packageFile))[packageFile]
                val expectedResult = patchExpectedResult(workingDir)

                npm.command(workingDir) shouldBe NPM.npm
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "show error if no lockfile is present" {
                val workingDir = File(projectDir, "no-lockfile")
                val packageFile = File(workingDir, "package.json")

                val result = NPM.create().resolveDependencies(listOf(packageFile))[packageFile]
                val expectedResult = patchExpectedResult(workingDir, "npm-expected-output-no-lockfile.yml")

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "show error if multiple lockfiles are present" {
                val workingDir = File(projectDir, "multiple-lockfiles")
                val packageFile = File(workingDir, "package.json")

                val result = NPM.create().resolveDependencies(listOf(packageFile))[packageFile]
                val expectedResult = patchExpectedResult(workingDir, "npm-expected-output-multiple-lockfiles.yml")

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "resolve dependencies even if the node_modules directory already exists" {
                val workingDir = File(projectDir, "node-modules")
                val packageFile = File(workingDir, "package.json")
                val npm = NPM.create()

                val result = npm.resolveDependencies(listOf(packageFile))[packageFile]
                val expectedResult = patchExpectedResult(workingDir)

                npm.command(workingDir) shouldBe NPM.npm
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }

        "yarn should" - {
            "resolve dependencies correctly" {
                val workingDir = File(projectDir, "yarn")
                val packageFile = File(workingDir, "package.json")
                val npm = NPM.create()

                val result = npm.resolveDependencies(listOf(packageFile))[packageFile]
                val expectedResult = patchExpectedResult(workingDir)

                npm.command(workingDir) shouldBe NPM.yarn
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }
    }
}
