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

package com.here.ort.analyzer.managers

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.Project
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.yamlMapper
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import com.here.ort.utils.test.USER_DIR

import io.kotlintest.matchers.startWith
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.WordSpec

import java.io.File

class GoDepTest : WordSpec() {
    private val projectsDir = File("src/funTest/assets/projects").absoluteFile

    init {
        "GoDep" should {
            "resolve dependencies from a lockfile correctly" {
                val manifestFile = File(projectsDir, "external/qmstr/Gopkg.toml")
                val result = createGoDep().resolveDependencies(listOf(manifestFile))[manifestFile]
                val expectedResult = File(projectsDir, "external/qmstr-expected-output.yml").readText()

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "show error if no lockfile is present" {
                val manifestFile = File(projectsDir, "synthetic/godep/no-lockfile/Gopkg.toml")
                val result = createGoDep().resolveDependencies(listOf(manifestFile))[manifestFile]

                result shouldNotBe null
                with(result!!) {
                    project.id shouldBe
                            Identifier("GoDep::src/funTest/assets/projects/synthetic/godep/no-lockfile/Gopkg.toml:")
                    project.definitionFilePath shouldBe
                            "analyzer/src/funTest/assets/projects/synthetic/godep/no-lockfile/Gopkg.toml"
                    packages.size shouldBe 0
                    errors.size shouldBe 1
                    errors.first().message should startWith("IllegalArgumentException: No lockfile found in")
                }
            }

            "invoke the dependency solver if no lockfile is present and allowDynamicVersions is set" {
                val manifestFile = File(projectsDir, "synthetic/godep/no-lockfile/Gopkg.toml")
                val config = AnalyzerConfiguration(ignoreToolVersions = false, allowDynamicVersions = true)
                val result = createGoDep(config).resolveDependencies(listOf(manifestFile))[manifestFile]

                result shouldNotBe null
                with(result!!) {
                    project shouldNotBe Project.EMPTY
                    packages.size shouldBe 4
                    errors.size shouldBe 0
                }
            }

            "import dependencies from Glide" {
                val manifestFile = File(projectsDir, "external/sprig/glide.yaml")
                val result = createGoDep().resolveDependencies(listOf(manifestFile))[manifestFile]
                val expectedResult = File(projectsDir, "external/sprig-expected-output.yml").readText()

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "import dependencies from godeps" {
                val manifestFile = File(projectsDir, "external/godep/Godeps/Godeps.json")
                val result = createGoDep().resolveDependencies(listOf(manifestFile))[manifestFile]
                val expectedResult = File(projectsDir, "external/godep-expected-output.yml").readText()

                yamlMapper.writeValueAsString(result) shouldBe expectedResult
            }

            "construct an import path from VCS info" {
                val gopath = File("/tmp/gopath").absoluteFile
                val projectDir = File(projectsDir, "external/qmstr")
                val vcs = VersionControlSystem.forDirectory(projectDir)!!.getInfo()

                val expectedPath = File("/tmp/gopath/src/github.com/QMSTR/qmstr.git").absoluteFile

                createGoDep().deduceImportPath(projectDir, vcs, gopath) shouldBe expectedPath
            }

            "construct an import path for directories that are not repositories" {
                val gopath = File("/tmp/gopath").absoluteFile
                val projectDir = File(projectsDir, "synthetic/godep/no-lockfile")
                val vcs = VcsInfo.EMPTY

                val expectedPath = File("/tmp/gopath/src/no-lockfile").absoluteFile

                createGoDep().deduceImportPath(projectDir, vcs, gopath) shouldBe expectedPath
            }
        }
    }

    private fun createGoDep(config: AnalyzerConfiguration = DEFAULT_ANALYZER_CONFIGURATION) =
        GoDep("GoDep", USER_DIR, config, DEFAULT_REPOSITORY_CONFIGURATION)
}
