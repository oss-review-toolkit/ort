/*
 * Copyright (c) 2017 HERE Europe B.V.
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
import com.here.ort.util.yamlMapper

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.endWith
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.matchers.startWith
import io.kotlintest.specs.WordSpec

import java.io.File

class NpmTest : WordSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/project-npm")
    private val vcsRevision = VersionControlSystem.fromDirectory(projectDir).first().getWorkingRevision(projectDir)

    @Suppress("CatchException")
    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        try {
            test()
        } catch (exception: Exception) {
            // Make sure the node_modules directory is always deleted from each subdirectory to prevent side-effects
            // from failing tests.
            projectDir.listFiles().forEach {
                if (it.isDirectory) {
                    val nodeModulesDir = File(it, "node_modules")
                    val gitKeepFile = File(nodeModulesDir, ".gitkeep")
                    if (nodeModulesDir.isDirectory && !gitKeepFile.isFile) {
                        nodeModulesDir.deleteRecursively()
                    }
                }
            }

            throw exception
        }
    }

    private fun patchExpectedResult(workingDir: File): String {
        val vcsPath = "analyzer/" + workingDir.path.replace("\\", "/")
        return File(projectDir.parentFile, "project-npm-expected-output.yml")
                .readText()
                .replaceFirst("project-npm", "project-npm-${workingDir.name}")
                .replaceFirst("vcs_path: \"\"", "vcs_path: \"$vcsPath\"")
                .replaceFirst("vcs_revision: \"\"", "vcs_revision: \"$vcsRevision\"")
    }

    init {
        "NPM" should {
            "resolve shrinkwrap dependencies correctly" {
                val workingDir = File(projectDir, "shrinkwrap")
                val packageFile = File(workingDir, "package.json")
                val npm = NPM.create()

                val result = npm.resolveDependencies(projectDir, listOf(packageFile))[packageFile]
                val expectedResult = patchExpectedResult(workingDir)

                npm.command(workingDir) shouldBe NPM.npm
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "resolve package-lock dependencies correctly" {
                val workingDir = File(projectDir, "package-lock")
                val packageFile = File(workingDir, "package.json")
                val npm = NPM.create()

                val result = npm.resolveDependencies(projectDir, listOf(packageFile))[packageFile]
                val expectedResult = patchExpectedResult(workingDir)

                npm.command(workingDir) shouldBe NPM.npm
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "abort if no lockfile is present" {
                val workingDir = File(projectDir, "no-lockfile")
                val packageFile = File(workingDir, "package.json")

                val exception = shouldThrow<IllegalArgumentException> {
                    NPM.create().resolveDependencies(projectDir, listOf(packageFile))
                }

                @Suppress("UnsafeCallOnNullableType")
                exception.message!! should startWith("No lockfile found in")
            }

            "abort if multiple lockfiles are present" {
                val workingDir = File(projectDir, "multiple-lockfiles")
                val packageFile = File(workingDir, "package.json")

                val exception = shouldThrow<IllegalArgumentException> {
                    NPM.create().resolveDependencies(projectDir, listOf(packageFile))
                }

                @Suppress("UnsafeCallOnNullableType")
                exception.message!! should endWith("contains multiple lockfiles. It is ambiguous which one to use.")
            }

            "resolve dependencies even if the node_modules directory already exists" {
                val workingDir = File(projectDir, "node-modules")
                val packageFile = File(workingDir, "package.json")
                val npm = NPM.create()

                val result = npm.resolveDependencies(projectDir, listOf(packageFile))[packageFile]
                val expectedResult = patchExpectedResult(workingDir)

                npm.command(workingDir) shouldBe NPM.npm
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }

        "yarn" should {
            "resolve dependencies correctly" {
                val workingDir = File(projectDir, "yarn")
                val packageFile = File(workingDir, "package.json")
                val npm = NPM.create()

                val result = npm.resolveDependencies(projectDir, listOf(packageFile))[packageFile]
                val expectedResult = patchExpectedResult(workingDir)

                npm.command(workingDir) shouldBe NPM.yarn
                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }
        }
    }
}
