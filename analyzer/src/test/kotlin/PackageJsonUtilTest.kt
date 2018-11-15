/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.here.ort.analyzer.managers.PackageJsonUtil
import com.here.ort.analyzer.managers.PackageJsonUtil.Companion.mapDefinitionFilesForNpm
import com.here.ort.analyzer.managers.PackageJsonUtil.Companion.mapDefinitionFilesForYarn
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.safeMkdirs

import io.kotlintest.Description
import io.kotlintest.TestResult
import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class PackageJsonUtilTest : WordSpec() {
    companion object {
        private fun createPackageJson(matchers: List<String>) =
                if (!matchers.isEmpty()) {
                    """
                    {
                       "workspaces" : ${matchers.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")}
                    }
                    """.trimIndent()
                } else {
                    "{}"
                }

        private fun mapDefinitionFiles(definitionFiles: Collection<File>) =
                mapDefinitionFilesForNpm(definitionFiles) + mapDefinitionFilesForYarn(definitionFiles)
    }

    init {
        "hasNpmLockFile" should {
            "return false if no NPM lockfile is present" {
                setupProject(path = "a")

                hasNpmLockFile("a") shouldBe false
            }

            "returns true if an NPM lockfile present" {
                setupProject(path = "a", hasNpmLockFile = true)

                hasNpmLockFile("a") shouldBe true
            }
        }

        "hasYarnLockFile" should {
            "return false if no Yarn lockfile is present" {
                setupProject(path = "a")

                hasNpmLockFile("a") shouldBe false
            }

            "returns true if a Yarn lockfile is present" {
                setupProject(path = "a", hasYarnLockFile = true)

                hasYarnLockFile("a") shouldBe true
            }
        }

        "Definition file mapping" should {
            "happen for Yarn only if both Yarn and NPM lockfiles are present" {
                setupProject(path = "a", hasNpmLockFile = true, hasYarnLockFile = true)

                mapDefinitionFilesForNpm(definitionFiles).shouldBeEmpty()
                mapDefinitionFilesForYarn(definitionFiles) shouldContainExactly absolutePaths("a/package.json")
            }

            "happen for NPM only if no lockfile is present" {
                setupProject(path = "a")

                mapDefinitionFilesForNpm(definitionFiles) shouldContainExactly absolutePaths("a/package.json")
                mapDefinitionFilesForYarn(definitionFiles).shouldBeEmpty()
            }
        }

        "Workspace projects" should {
            "not be mapped if the project path matches literally" {
                setupProject(path = "a", matchers = listOf("b"))
                setupProject(path = "a/b")
                setupProject(path = "a/c")

                mapDefinitionFiles(definitionFiles) shouldContainExactlyInAnyOrder
                        absolutePaths("a/package.json", "a/c/package.json")
            }

            "not be mapped if * matches the project path" {
                setupProject(path = "a", matchers = listOf("*", "*/f"))
                setupProject(path = "a/b")
                setupProject(path = "a/c")
                setupProject(path = "a/d/e")
                setupProject(path = "a/d/f")

                mapDefinitionFiles(definitionFiles) shouldContainExactlyInAnyOrder
                        absolutePaths("a/package.json", "a/d/e/package.json")
            }

            "not be mapped if ** matches the project name" {
                setupProject(path = "a", matchers = listOf("**/d"))
                setupProject(path = "a/b/c/d")
                setupProject(path = "a/b/c/e")

                mapDefinitionFiles(definitionFiles) shouldContainExactlyInAnyOrder
                        absolutePaths("a/package.json", "a/b/c/e/package.json")
            }
        }
    }

    private lateinit var tempDir: File
    private val definitionFiles = mutableSetOf<File>()

    override fun beforeTest(description: Description) {
        super.beforeTest(description)
        tempDir = createTempDir()
        definitionFiles.clear()
    }

    override fun afterTest(description: Description, result: TestResult) {
        tempDir.safeDeleteRecursively()
        definitionFiles.clear()
        super.afterTest(description, result)
    }

    private fun setupProject(path: String, matchers: List<String> = emptyList(), hasNpmLockFile: Boolean = false,
                             hasYarnLockFile: Boolean = false
    ) {
        val projectDir = tempDir.resolve(path)

        require(!projectDir.exists())
        projectDir.safeMkdirs()

        val definitionFile = projectDir.resolve("package.json")
        definitionFile.writeText(createPackageJson(matchers))
        definitionFiles.add(definitionFile)

        if (hasNpmLockFile) projectDir.resolve("package-lock.json").createNewFile()
        if (hasYarnLockFile) projectDir.resolve("yarn.lock").createNewFile()
    }

    private fun absolutePaths(vararg files: String) =
            files.asList().map { file ->
                tempDir.resolve(file)
            }

    private fun hasNpmLockFile(path: String) = PackageJsonUtil.hasNpmLockFile(tempDir.resolve(path))

    private fun hasYarnLockFile(path: String) = PackageJsonUtil.hasYarnLockFile(tempDir.resolve(path))
}
