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

package com.here.ort.analyzer.vcs

import com.here.ort.analyzer.Analyzer
import com.here.ort.analyzer.managers.Bundler
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.downloader.vcs.GitRepo
import com.here.ort.model.Package
import com.here.ort.model.VcsInfo
import com.here.ort.model.yamlMapper
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.patchActualResult
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = "https://github.com/heremaps/oss-review-toolkit-test-data-git-repo"
private const val REPO_REV = "f3e4aba383260627c8277cd1c2262bd27deeea2b"
private const val REPO_MANIFEST = "manifest.xml"

class GitRepoTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeSpec(spec: Spec) {
        outputDir = createTempDir()

        val vcs = VcsInfo("GitRepo", REPO_URL, REPO_REV, path = REPO_MANIFEST)
        val pkg = Package.EMPTY.copy(vcsProcessed = vcs)

        GitRepo().download(pkg, outputDir)
    }

    override fun afterSpec(spec: Spec) {
        outputDir.safeDeleteRecursively(force = true)
    }

    init {
        "Analyzer correctly reports GitRepo VcsInfo for Bundler projects" {
            val ortResult = Analyzer(DEFAULT_ANALYZER_CONFIGURATION).analyze(outputDir, listOf(Bundler.Factory()))
            val actualResult = yamlMapper.writeValueAsString(ortResult)
            val expectedResult = patchExpectedResult(
                    File("src/funTest/assets/projects/external/grpc-bundler-expected-output.yml"),
                    revision = REPO_REV,
                    path = outputDir.invariantSeparatorsPath)

            patchActualResult(actualResult, patchStartAndEndTime = true) shouldBe expectedResult
        }

        "GitRepo correctly lists submodules" {
            val expectedSubmodules = listOf(
                    "grpc",
                    "grpc/third_party/benchmark",
                    "grpc/third_party/boringssl",
                    "grpc/third_party/boringssl-with-bazel",
                    "grpc/third_party/cares/cares",
                    "grpc/third_party/gflags",
                    "grpc/third_party/gflags/doc",
                    "grpc/third_party/googletest",
                    "grpc/third_party/protobuf",
                    "grpc/third_party/protobuf/third_party/benchmark",
                    "grpc/third_party/zlib",
                    "spdx-tools"
            ).associateWith { VersionControlSystem.getPathInfo(File(outputDir, it)) }

            val workingTree = GitRepo().getWorkingTree(outputDir)
            workingTree.getNested() shouldBe expectedSubmodules
        }
    }
}
