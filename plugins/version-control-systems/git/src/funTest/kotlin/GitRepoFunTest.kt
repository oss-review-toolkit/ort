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

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.sequences.shouldContainExactly
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.common.div

import org.semver4j.Semver
import org.semver4j.SemverException

private const val REPO_URL = "https://github.com/oss-review-toolkit/ort-test-data-git-repo?manifest=manifest.xml"
private const val REPO_REV = "31588aa8f8555474e1c3c66a359ec99e4cd4b1fa"

@Tags("RequiresExternalTool")
class GitRepoFunTest : WordSpec({
    val gitRepo = GitRepoFactory().create(PluginConfig.EMPTY)
    val vcs = VcsInfo(VcsType.GIT_REPO, REPO_URL, REPO_REV)
    val pkg = Package.EMPTY.copy(vcsProcessed = vcs)

    lateinit var outputDir: File

    beforeEach {
        outputDir = tempdir()
    }

    "getVersion()" should {
        "return a version that can be coerced to a Semver" {
            shouldNotThrow<SemverException> {
                Semver.coerce(gitRepo.getVersion())
            }
        }
    }

    "download()" should {
        "get the given revision" {
            val spdxDir = outputDir / "spdx-tools"

            val actualSpdxFiles = spdxDir.walk().maxDepth(1).filter {
                it.isDirectory && it != spdxDir
            }.map {
                it.name
            }.sorted()

            val submodulesDir = outputDir / "submodules"

            val actualSubmodulesFiles = submodulesDir.walk().maxDepth(1).filter {
                it.isDirectory && it != submodulesDir
            }.map {
                it.name
            }.sorted()

            val workingTree = gitRepo.download(pkg, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getInfo() shouldBe vcs

            workingTree.getPathToRoot(outputDir / "grpc" / "README.md") shouldBe "grpc/README.md"
            workingTree.getPathToRoot(outputDir / "spdx-tools" / "TODO") shouldBe "spdx-tools/TODO"

            actualSpdxFiles.shouldContainExactly(
                ".git",
                "Examples",
                "Test",
                "TestFiles",
                "doc",
                "resources",
                "src"
            )

            actualSubmodulesFiles.shouldContainExactly(
                ".git",
                "commons-text",
                "test-data-npm"
            )
        }

        "get nested submodules" {
            val workingTree = gitRepo.download(pkg, outputDir)

            workingTree.getNested() shouldContainExactly listOf(
                "spdx-tools",
                "submodules",
                "submodules/commons-text",
                "submodules/test-data-npm",
                "submodules/test-data-npm/isarray",
                "submodules/test-data-npm/long.js"
            ).associateWith {
                VersionControlSystem.getPathInfo(outputDir / it)
            }
        }
    }
})
