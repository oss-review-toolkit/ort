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

package org.ossreviewtoolkit.analyzer

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.analyzer.managers.toYaml
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.vcs.GitRepo
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.convertToDependencyGraph
import org.ossreviewtoolkit.utils.test.createSpecTempDir
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

private const val REPO_URL = "https://github.com/oss-review-toolkit/ort-test-data-git-repo"
private const val REPO_REV = "31588aa8f8555474e1c3c66a359ec99e4cd4b1fa"
private const val REPO_MANIFEST = "manifest.xml"

class GitRepoFunTest : StringSpec() {
    private val outputDir = createSpecTempDir()

    override fun beforeSpec(spec: Spec) {
        val vcs = VcsInfo(VcsType.GIT_REPO, REPO_URL, REPO_REV, path = REPO_MANIFEST)
        val pkg = Package.EMPTY.copy(vcsProcessed = vcs)

        GitRepo().download(pkg, outputDir)
    }

    init {
        "Analyzer correctly reports VcsInfo for git-repo projects" {
            val ortResult = Analyzer(DEFAULT_ANALYZER_CONFIGURATION).analyze(outputDir)
            val actualResult = ortResult.withResolvedScopes().toYaml()
            val expectedResult = convertToDependencyGraph(
                patchExpectedResult(
                    File("src/funTest/assets/projects/external/git-repo-expected-output.yml"),
                    revision = REPO_REV,
                    path = outputDir.invariantSeparatorsPath
                )
            )

            patchActualResult(actualResult, patchStartAndEndTime = true) shouldBe expectedResult
        }

        "GitRepo correctly lists submodules" {
            val expectedSubmodules = listOf(
                "spdx-tools",
                "submodules",
                "submodules/commons-text",
                "submodules/test-data-npm",
                "submodules/test-data-npm/isarray",
                "submodules/test-data-npm/long.js"
            ).associateWith { VersionControlSystem.getPathInfo(outputDir.resolve(it)) }

            val workingTree = GitRepo().getWorkingTree(outputDir)
            workingTree.getNested() shouldBe expectedSubmodules
        }
    }
}
