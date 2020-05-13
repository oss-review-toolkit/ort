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

package org.ossreviewtoolkit.analyzer.managers.utils

import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.safeMkdirs

import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll

import java.io.File

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

        "Definition file mapping" should {
            "happen for Yarn only if both Yarn and NPM lockfiles are present" {
                setupProject(path = "a", hasNpmLockFile = true, hasYarnLockFile = true)

                mapDefinitionFilesForNpm(definitionFiles) should beEmpty()
                mapDefinitionFilesForYarn(definitionFiles) shouldContainExactly absolutePaths("a/package.json")
            }

            "happen for NPM only if no lockfile is present" {
                setupProject(path = "a")

                mapDefinitionFilesForNpm(definitionFiles) shouldContainExactly absolutePaths("a/package.json")
                mapDefinitionFilesForYarn(definitionFiles) should beEmpty()
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

            "not be mapped if * matches the project path (non-flattened workspace definition)" {
                setupProject(path = "a", matchers = listOf("*", "*/f"), flattenWorkspaceDefinition = false)
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

            "not be mapped if ** matches the project name (non-flattened workspace definition)" {
                setupProject(path = "a", matchers = listOf("**/d"), flattenWorkspaceDefinition = false)
                setupProject(path = "a/b/c/d")
                setupProject(path = "a/b/c/e")

                mapDefinitionFiles(definitionFiles) shouldContainExactlyInAnyOrder
                        absolutePaths("a/package.json", "a/b/c/e/package.json")
            }
        }

        "expandNpmShortcutURL" should {
            "do nothing for empty URLs" {
                expandNpmShortcutURL("") shouldBe ""
            }

            "properly handle NPM shortcut URLs" {
                val packages = mapOf(
                    "npm/npm"
                            to "https://github.com/npm/npm.git",
                    "mochajs/mocha#4727d357ea"
                            to "https://github.com/mochajs/mocha.git#4727d357ea",
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
                    expandNpmShortcutURL(actualUrl) shouldBe expectedUrl
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
                    expandNpmShortcutURL(actualUrl) shouldBe expectedUrl
                }
            }
        }

        "readProxySettingFromNpmRc" should {
            "properly read proxy configuration" {
                readProxySettingFromNpmRc("proxy=http://user:password@host.domain.com:8080/") shouldBe
                        "http://user:password@host.domain.com:8080/"
                readProxySettingFromNpmRc("https-proxy=http://user:password@host.domain.com:8080/") shouldBe
                        "http://user:password@host.domain.com:8080/"

                readProxySettingFromNpmRc("proxy=http://user:password@host.domain.com") shouldBe
                        "http://user:password@host.domain.com"
                readProxySettingFromNpmRc("https-proxy=http://user:password@host.domain.com") shouldBe
                        "http://user:password@host.domain.com"

                readProxySettingFromNpmRc("proxy=user:password@host.domain.com") shouldBe
                        "http://user:password@host.domain.com"
                readProxySettingFromNpmRc("https-proxy=user:password@host.domain.com") shouldBe
                        "http://user:password@host.domain.com"
            }

            "ignore non-proxy URLs" {
                readProxySettingFromNpmRc("registry=http://my.artifactory.com/artifactory/api/npm/npm-virtual") shouldBe
                        null
            }
        }
    }

    private lateinit var tempDir: File
    private val definitionFiles = mutableSetOf<File>()

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        tempDir = createTempDir(ORT_NAME, javaClass.simpleName)
        definitionFiles.clear()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        tempDir.safeDeleteRecursively(force = true)
        definitionFiles.clear()
        super.afterTest(testCase, result)
    }

    private fun setupProject(
        path: String, matchers: List<String> = emptyList(), hasNpmLockFile: Boolean = false,
        hasYarnLockFile: Boolean = false, flattenWorkspaceDefinition: Boolean = true
    ) {
        val projectDir = tempDir.resolve(path)

        require(!projectDir.exists())
        projectDir.safeMkdirs()

        val definitionFile = projectDir.resolve("package.json")
        definitionFile.writeText(createPackageJson(matchers, flattenWorkspaceDefinition))
        definitionFiles.add(definitionFile)

        if (hasNpmLockFile) projectDir.resolve("package-lock.json").createNewFile()
        if (hasYarnLockFile) projectDir.resolve("yarn.lock").createNewFile()
    }

    private fun absolutePaths(vararg files: String) =
        files.asList().map { file ->
            tempDir.resolve(file)
        }

    private fun hasNpmLockFile(path: String) = hasNpmLockFile(tempDir.resolve(path))

    private fun hasYarnLockFile(path: String) = hasYarnLockFile(tempDir.resolve(path))
}
