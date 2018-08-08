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

package com.here.ort.analyzer.vcs

import com.here.ort.analyzer.Analyzer
import com.here.ort.analyzer.managers.Bundler
import com.here.ort.downloader.vcs.GitRepo
import com.here.ort.model.Package
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.yamlMapper
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.patchActualResult
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.Description
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = "https://github.com/heremaps/oss-review-toolkit-test-data"
private const val REPO_REV = "1c0b86e578349f38acd43de354f815370112a213"
private const val REPO_MANIFEST = "git-repo/manifest.xml"

class GitRepoTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeTest(description: Description) {
        outputDir = createTempDir()
    }

    override fun afterTest(description: Description, result: TestResult) {
        outputDir.safeDeleteRecursively()
    }

    init {
        "Analyzer correctly reports GitRepo VcsInfo for Bundler projects" {
            val vcs = VcsInfo("GitRepo", REPO_URL, REPO_REV, path = REPO_MANIFEST)
            val pkg = Package.EMPTY.copy(vcsProcessed = vcs)

            GitRepo.download(pkg, outputDir)
            val config = AnalyzerConfiguration(false, false, false)
            val ortResult = Analyzer().analyze(config, outputDir, listOf(Bundler))
            val actualResult = yamlMapper.writeValueAsString(ortResult)
            val expectedResult = patchExpectedResult(
                    File("src/funTest/assets/projects/external/grpc-bundler-expected-output.yml"),
                    custom = Pair("<REPLACE_TMP>", outputDir.name),
                    path = outputDir.invariantSeparatorsPath)

            patchActualResult(actualResult) shouldBe expectedResult
        }
    }
}
