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
import com.here.ort.model.yamlMapper
import com.here.ort.utils.ExpensiveTag
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.searchUpwardsForSubdirectory

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class BundlerTest : StringSpec() {
    private val rootDir = File(".").searchUpwardsForSubdirectory(".git")!!
    private val projectDir = File(rootDir, "analyzer/src/funTest/assets/projects/synthetic/bundler")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsRevision = vcsDir.getRevision()
    private val vcsUrl = vcsDir.getRemoteUrl()

    init {
        "Project dependencies are detected correctly" {
            try {
                val packageFile = File(projectDir, "Gemfile")

                val actualResult = Bundler.create().resolveDependencies(listOf(packageFile))[packageFile]
                val expectedResult = patchExpectedResult(File(projectDir.parentFile, "bundler-expected-output.yml")
                        .readText())

                yamlMapper.writeValueAsString(actualResult) shouldBe expectedResult
            } finally {
                File(projectDir, "Gemfile.lock").delete()
                File(projectDir, ".bundle").safeDeleteRecursively()
            }
        }.config(tags = setOf(ExpensiveTag))
    }

    private fun patchExpectedResult(result: String): String {
        val vcsPath = projectDir.relativeTo(rootDir).invariantSeparatorsPath

        return result
                // vcs
                .replaceFirst("<REPLACE_URL>", vcsUrl)
                .replaceFirst("<REPLACE_REVISION>", vcsRevision)
                .replaceFirst("<REPLACE_PATH>", vcsPath)
                // vcs_processed:
                .replaceFirst("<REPLACE_URL>", normalizeVcsUrl(vcsUrl))
                .replaceFirst("<REPLACE_REVISION>", vcsRevision)
                .replaceFirst("<REPLACE_PATH>", vcsPath)
    }
}
