/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.npm

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.EqMatcher
import io.mockk.VarargMatcher
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor

import java.io.File

import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.ProcessCapture

class NpmTest : WordSpec({
    "NPM command output" should {
        "be parsed correctly" {
            val workingDir = tempdir()
            val definitionFileSrc = File("src/test/assets/test-package-no-deps.json")
            val definitionFile = workingDir.resolve("package.json")
            definitionFileSrc.copyTo(definitionFile)

            val errorFile = File("src/test/assets/npm-err.txt")
            val errorText = errorFile.readText()

            mockkConstructor(ProcessCapture::class)
            try {
                val analyzerName = "test-npm"

                val npmOptions = mapOf("legacyPeerDeps" to "true")
                val npmConfig = PackageManagerConfiguration(options = npmOptions)

                val analyzerConfig = AnalyzerConfiguration(
                    allowDynamicVersions = true,
                    packageManagers = mapOf(analyzerName to npmConfig)
                )

                val npm = Npm(analyzerName, workingDir, analyzerConfig, RepositoryConfiguration())

                every {
                    constructedWith<ProcessCapture>(
                        EqMatcher(workingDir),
                        VarargMatcher(
                            all = true,
                            matcher = { true },
                            prefix = listOf(
                                EqMatcher(npm.command()),
                                EqMatcher("install"),
                                EqMatcher("--ignore-scripts"),
                                EqMatcher("--no-audit"),
                                EqMatcher("--legacy-peer-deps")
                            )
                        )
                    ).stderr
                } returns errorText

                val results = npm.resolveDependencies(definitionFile, emptyMap())

                results shouldHaveSize 1

                with(results[0]) {
                    packages should beEmpty()
                    issues shouldHaveSize 1
                    issues[0].severity shouldBe Severity.ERROR
                }
            } finally {
                unmockkConstructor(ProcessCapture::class)
            }
        }
    }

    "groupLines()" should {
        "remove common prefixes from NPM warnings" {
            val output = """
                npm warn old lockfile
                npm warn old lockfile The npm-shrinkwrap.json file was created with an old version of npm,
                npm warn old lockfile so supplemental metadata must be fetched from the registry.
                npm warn old lockfile
                npm warn old lockfile This is a one-time fix-up, please be patient...
                npm warn old lockfile
                npm warn deprecated coffee-script@1.12.7: CoffeeScript on NPM has moved to "coffeescript" (no hyphen)
            """.trimIndent()

            output.lines().groupLines("npm warn ") shouldBe listOf(
                "The npm-shrinkwrap.json file was created with an old version of npm, so supplemental metadata must " +
                    "be fetched from the registry. This is a one-time fix-up, please be patient...",
                "deprecated coffee-script@1.12.7: CoffeeScript on NPM has moved to \"coffeescript\" (no hyphen)"
            )
        }

        "treat a single block of errors as one issue" {
            val output = """
                npm ERR! code EEXIST
                npm ERR! syscall mkdir
                npm ERR! path G:\Git\lsp-sample\node_modules.staging
                npm ERR! errno -4075
                npm ERR! EEXIST: file already exists, mkdir 'G:\Git\lsp-sample\node_modules.staging'
                npm ERR! File exists: G:\Git\lsp-sample\node_modules.staging
                npm ERR! Remove the existing file and try again, or run npm
                npm ERR! with --force to overwrite files recklessly.
            """.trimIndent()

            output.lines().groupLines("npm ERR! ") shouldBe listOf(
                "EEXIST: file already exists, mkdir 'G:\\Git\\lsp-sample\\node_modules.staging' " +
                    "File exists: G:\\Git\\lsp-sample\\node_modules.staging " +
                    "Remove the existing file and try again, or run npm " +
                    "with --force to overwrite files recklessly."
            )
        }
    }
})
