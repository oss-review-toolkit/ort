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

package org.ossreviewtoolkit.analyzer.managers.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.test.createTestTempDir

class NodeSupportTest : WordSpec() {
    companion object {
        private fun createPackageJson(matchers: List<String>, flattenWorkspaceDefinition: Boolean) =
            if (matchers.isNotEmpty()) {
                val workspaces = matchers.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")
                if (flattenWorkspaceDefinition) {
                    "{ \"workspaces\": $workspaces }"
                } else {
                    "{ \"workspaces\": { \"packages\": $workspaces } }"
                }
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

            "return true if an NPM lockfile present" {
                setupProject(path = "a", hasNpmLockFile = true)

                hasNpmLockFile("a") shouldBe true
            }
        }

        "hasYarnLockFile" should {
            "return false if no Yarn lockfile is present" {
                setupProject(path = "a")

                hasNpmLockFile("a") shouldBe false
            }

            "return true if a Yarn lockfile is present" {
                setupProject(path = "a", hasYarnLockFile = true)

                hasYarnLockFile("a") shouldBe true
            }
        }

        "hasYarn2ResourceFile" should {
            "return false if no Yarn 2+ resource file is present" {
                setupProject(path = "a")

                hasYarn2ResourceFile("a") shouldBe false
            }

            "return true if a Yarn 2+ resource file is present" {
                setupProject(path = "a", hasYarn2ResourceFile = true)

                hasYarn2ResourceFile("a") shouldBe true
            }
        }

        "Definition file mapping" should {
            "happen for Yarn 1 only if both Yarn and NPM lockfiles are present, but without a Yarn 2+ resource file" {
                setupProject(path = "a", hasNpmLockFile = true, hasYarnLockFile = true)

                mapDefinitionFilesForNpm(definitionFiles) should beEmpty()
                mapDefinitionFilesForYarn(definitionFiles) should containExactly(absolutePaths("a/package.json"))
                mapDefinitionFilesForYarn2(definitionFiles) should beEmpty()
            }

            "happen for Yarn 2+ only if both Yarn 2+ resource file and Yarn lockfile are present" {
                setupProject(path = "a", hasYarnLockFile = true, hasYarn2ResourceFile = true)

                mapDefinitionFilesForNpm(definitionFiles) should beEmpty()
                mapDefinitionFilesForYarn(definitionFiles) should beEmpty()
                mapDefinitionFilesForYarn2(definitionFiles) should containExactly(absolutePaths("a/package.json"))
            }

            "happen for NPM only if no lockfile is present" {
                setupProject(path = "a")

                mapDefinitionFilesForNpm(definitionFiles) should containExactly(absolutePaths("a/package.json"))
                mapDefinitionFilesForYarn(definitionFiles) should beEmpty()
                mapDefinitionFilesForYarn2(definitionFiles) should beEmpty()
            }
        }

        "Workspace projects" should {
            "not be mapped if the project path matches literally" {
                setupProject(path = "a", matchers = listOf("b"))
                setupProject(path = "a/b")
                setupProject(path = "a/c")

                mapDefinitionFiles(definitionFiles) should containExactlyInAnyOrder(
                    absolutePaths("a/package.json", "a/c/package.json")
                )
            }

            "not be mapped if * matches the project path" {
                setupProject(path = "a", matchers = listOf("*", "*/f"))
                setupProject(path = "a/b")
                setupProject(path = "a/c")
                setupProject(path = "a/d/e")
                setupProject(path = "a/d/f")

                mapDefinitionFiles(definitionFiles) should containExactlyInAnyOrder(
                    absolutePaths("a/package.json", "a/d/e/package.json")
                )
            }

            "not be mapped if * matches the project path (non-flattened workspace definition)" {
                setupProject(path = "a", matchers = listOf("*", "*/f"), flattenWorkspaceDefinition = false)
                setupProject(path = "a/b")
                setupProject(path = "a/c")
                setupProject(path = "a/d/e")
                setupProject(path = "a/d/f")

                mapDefinitionFiles(definitionFiles) should containExactlyInAnyOrder(
                    absolutePaths("a/package.json", "a/d/e/package.json")
                )
            }

            "not be mapped if ** matches the project name" {
                setupProject(path = "a", matchers = listOf("**/d"))
                setupProject(path = "a/b/c/d")
                setupProject(path = "a/b/c/e")

                mapDefinitionFiles(definitionFiles) should containExactlyInAnyOrder(
                    absolutePaths("a/package.json", "a/b/c/e/package.json")
                )
            }

            "not be mapped if ** matches the project name (non-flattened workspace definition)" {
                setupProject(path = "a", matchers = listOf("**/d"), flattenWorkspaceDefinition = false)
                setupProject(path = "a/b/c/d")
                setupProject(path = "a/b/c/e")

                mapDefinitionFiles(definitionFiles) should containExactlyInAnyOrder(
                    absolutePaths("a/package.json", "a/b/c/e/package.json")
                )
            }
        }

        "expandNpmShortcutUrl" should {
            "do nothing for empty URLs" {
                expandNpmShortcutUrl("") shouldBe ""
            }

            "return valid URLs unmodified" {
                expandNpmShortcutUrl("https://github.com/oss-review-toolkit/ort") shouldBe
                        "https://github.com/oss-review-toolkit/ort"
            }

            "properly handle NPM shortcut URLs" {
                val packages = mapOf(
                    "npm/npm"
                            to "https://github.com/npm/npm.git",
                    "mochajs/mocha#4727d357ea"
                            to "https://github.com/mochajs/mocha.git#4727d357ea",
                    "user/repo#feature/branch"
                            to "https://github.com/user/repo.git#feature/branch",
                    "github:snyk/node-tap#540c9e00f52809cb7fbfd80463578bf9d08aad50"
                            to "https://github.com/snyk/node-tap.git#540c9e00f52809cb7fbfd80463578bf9d08aad50",
                    "gist:11081aaa281"
                            to "https://gist.github.com/11081aaa281",
                    "bitbucket:example/repo"
                            to "https://bitbucket.org/example/repo.git",
                    "gitlab:another/repo"
                            to "https://gitlab.com/another/repo.git"
                )

                packages.entries.forAll { (actualUrl, expectedUrl) ->
                    expandNpmShortcutUrl(actualUrl) shouldBe expectedUrl
                }
            }

            "not mess with crazy URLs" {
                val packages = mapOf(
                    "git@github.com/cisco/node-jose.git"
                            to "git@github.com/cisco/node-jose.git",
                    "https://git@github.com:hacksparrow/node-easyimage.git"
                            to "https://git@github.com:hacksparrow/node-easyimage.git",
                    "github.com/improbable-eng/grpc-web"
                            to "github.com/improbable-eng/grpc-web"
                )

                packages.entries.forAll { (actualUrl, expectedUrl) ->
                    expandNpmShortcutUrl(actualUrl) shouldBe expectedUrl
                }
            }
        }
    }

    private lateinit var tempDir: File
    private val definitionFiles = mutableSetOf<File>()

    override suspend fun beforeEach(testCase: TestCase) {
        tempDir = createTestTempDir()
        definitionFiles.clear()
    }

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        definitionFiles.clear()
    }

    private fun setupProject(
        path: String,
        matchers: List<String> = emptyList(),
        hasNpmLockFile: Boolean = false,
        hasYarnLockFile: Boolean = false,
        hasYarn2ResourceFile: Boolean = false,
        flattenWorkspaceDefinition: Boolean = true
    ) {
        val projectDir = tempDir.resolve(path)

        require(!projectDir.exists())
        projectDir.safeMkdirs()

        val definitionFile = projectDir.resolve("package.json")
        definitionFile.writeText(createPackageJson(matchers, flattenWorkspaceDefinition))
        definitionFiles += definitionFile

        if (hasNpmLockFile) projectDir.resolve("package-lock.json").createNewFile()
        if (hasYarnLockFile) projectDir.resolve("yarn.lock").createNewFile()
        if (hasYarn2ResourceFile) projectDir.resolve(".yarnrc.yml").createNewFile()
    }

    private fun absolutePaths(vararg files: String): Collection<File> = files.map { tempDir.resolve(it) }

    private fun hasNpmLockFile(path: String) = hasNpmLockFile(tempDir.resolve(path))

    private fun hasYarnLockFile(path: String) = hasYarnLockFile(tempDir.resolve(path))

    private fun hasYarn2ResourceFile(path: String) = hasYarn2ResourceFile(tempDir.resolve(path))
}
