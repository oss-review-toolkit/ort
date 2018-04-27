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

import com.here.ort.analyzer.managers.Bundler
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Project
import com.here.ort.model.yamlMapper
import com.here.ort.utils.ExpensiveTag
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.searchUpwardsForSubdirectory

import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.matchers.startWith
import io.kotlintest.specs.WordSpec

import java.io.File

class BundlerTest : WordSpec() {
    private val rootDir = File(".").searchUpwardsForSubdirectory(".git")!!
    private val projectsDir = File(rootDir, "analyzer/src/funTest/assets/projects/synthetic/bundler")
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsRevision = vcsDir.getRevision()
    private val vcsUrl = vcsDir.getRemoteUrl()

    init {
        "Bundler" should {
            "resolve dependencies correctly" {
                val definitionFile = File(projectsDir, "lockfile/Gemfile")

                try {
                    val actualResult = Bundler.create().resolveDependencies(listOf(definitionFile))[definitionFile]
                    val expectedResult = patchExpectedResult(definitionFile.parentFile, File(projectsDir.parentFile,
                            "bundler-expected-output.yml").readText())

                    yamlMapper.writeValueAsString(actualResult) shouldBe expectedResult
                } finally {
                    File(definitionFile.parentFile, ".bundle").safeDeleteRecursively()
                }
            }.config(tags = setOf(ExpensiveTag))

            "show error if no lockfile is present" {
                val definitionFile = File(projectsDir, "no-lockfile/Gemfile")

                val actualResult = Bundler.create().resolveDependencies(listOf(definitionFile))[definitionFile]

                actualResult shouldNotBe null
                actualResult!!.project shouldBe Project.EMPTY
                actualResult.packages.size shouldBe 0
                actualResult.errors.size shouldBe 1
                actualResult.errors.first() should startWith("IllegalArgumentException: No lockfile found in")
            }.config(tags = setOf(ExpensiveTag))
        }
    }

    private fun patchExpectedResult(workingDir: File, result: String): String {
        val vcsPath = workingDir.relativeTo(rootDir).invariantSeparatorsPath

        return result
                // vcs_processed:
                .replaceFirst("<REPLACE_URL>", normalizeVcsUrl(vcsUrl))
                .replaceFirst("<REPLACE_REVISION>", vcsRevision)
                .replaceFirst("<REPLACE_PATH>", vcsPath)
    }
}
