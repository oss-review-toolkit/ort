/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.versioncontrolsystems.git

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.common.div

private const val REPO_URL = "https://github.com/oss-review-toolkit/ort-test-data-git-repo?manifest=manifest.xml"
private const val REPO_REV = "31588aa8f8555474e1c3c66a359ec99e4cd4b1fa"

class GitRepoDownloadFunTest : StringSpec() {
    private val vcs = VcsInfo(VcsType.GIT_REPO, REPO_URL, REPO_REV)
    private val pkg = Package.EMPTY.copy(vcsProcessed = vcs)

    private lateinit var outputDir: File
    private lateinit var workingTree: WorkingTree

    override suspend fun beforeSpec(spec: Spec) {
        outputDir = tempdir()
        workingTree = GitRepoFactory().create(PluginConfig.EMPTY).download(pkg, outputDir)
    }

    init {
        "GitRepo can download a given revision" {
            val spdxDir = outputDir / "spdx-tools"
            val expectedSpdxFiles = listOf(
                ".git",
                "Examples",
                "Test",
                "TestFiles",
                "doc",
                "resources",
                "src"
            )

            val actualSpdxFiles = spdxDir.walk().maxDepth(1).filter {
                it.isDirectory && it != spdxDir
            }.map {
                it.name
            }.sorted()

            val submodulesDir = outputDir / "submodules"
            val expectedSubmodulesFiles = listOf(
                ".git",
                "commons-text",
                "test-data-npm"
            )

            val actualSubmodulesFiles = submodulesDir.walk().maxDepth(1).filter {
                it.isDirectory && it != submodulesDir
            }.map {
                it.name
            }.sorted()

            workingTree.isValid() shouldBe true
            workingTree.getInfo() shouldBe vcs

            workingTree.getPathToRoot(outputDir / "grpc" / "README.md") shouldBe "grpc/README.md"
            workingTree.getPathToRoot(outputDir / "spdx-tools" / "TODO") shouldBe "spdx-tools/TODO"

            actualSpdxFiles.joinToString("\n") shouldBe expectedSpdxFiles.joinToString("\n")
            actualSubmodulesFiles.joinToString("\n") shouldBe expectedSubmodulesFiles.joinToString("\n")
        }

        "GitRepo correctly lists submodules" {
            val expectedSubmodules = listOf(
                "spdx-tools",
                "submodules",
                "submodules/commons-text",
                "submodules/test-data-npm",
                "submodules/test-data-npm/isarray",
                "submodules/test-data-npm/long.js"
            ).associateWith { VersionControlSystem.getPathInfo(outputDir / it) }

            val workingTree = GitRepoFactory().create(PluginConfig.EMPTY).getWorkingTree(outputDir)
            workingTree.getNested() shouldBe expectedSubmodules
        }
    }
}
