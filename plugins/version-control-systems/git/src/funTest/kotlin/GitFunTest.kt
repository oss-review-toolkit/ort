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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.concurrent.shouldCompleteWithin
import io.kotest.matchers.shouldBe

import java.io.File
import java.util.concurrent.TimeUnit

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.PluginConfig

import org.semver4j.Semver
import org.semver4j.SemverException

private const val PKG_VERSION = "0.4.1"

private const val REPO_URL = "https://github.com/jriecken/dependency-graph"
private const val REPO_REV = "8964880d9bac33f0a7f030a74c7c9299a8f117c8"
private const val REPO_PATH = "lib"

private const val REPO_REV_FOR_VERSION = "371b23f37da064687518bace268d607a92ecbe8f"
private const val REPO_PATH_FOR_VERSION = "specs"

@Tags("RequiresExternalTool")
class GitFunTest : WordSpec({
    val git = GitFactory().create(PluginConfig.EMPTY)
    lateinit var outputDir: File

    beforeEach {
        outputDir = tempdir()
    }

    "getVersion()" should {
        "return a version that can be coerced to a Semver" {
            shouldNotThrow<SemverException> {
                Semver.coerce(git.getVersion())
            }
        }
    }

    "download()" should {
        "not prompt for credentials for non-existing repositories" {
            val url = "https://github.com/oss-review-toolkit/foobar.git"
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.GIT, url, "master"))

            val exception = shouldThrow<DownloadException> {
                git.download(pkg, outputDir, allowMovingRevisions = true)
            }

            exception.message shouldBe "Git failed to get revisions from URL $url."
        }

        "get the given revision" {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.GIT, REPO_URL, REPO_REV))

            val workingTree = git.download(pkg, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            workingTree.getRootPath().walk().maxDepth(1).mapNotNullTo(mutableListOf()) { file ->
                file.toRelativeString(outputDir).takeIf { it.isNotEmpty() && !it.startsWith('.') }
            }.shouldContainExactlyInAnyOrder(
                "CHANGELOG.md",
                "LICENSE",
                "README.md",
                "lib",
                "package.json",
                "specs"
            )
        }

        "get only the given path" {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.GIT, REPO_URL, REPO_REV, path = REPO_PATH))

            val workingTree = git.download(pkg, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            workingTree.getRootPath().walk().mapNotNullTo(mutableListOf()) { file ->
                file.toRelativeString(outputDir).takeIf { it.isNotEmpty() && !it.startsWith('.') }
            }.shouldContainExactlyInAnyOrder(
                "LICENSE",
                "README.md",
                "lib",
                "lib/dep_graph.js",
                "lib/index.d.ts"
            )
        }

        "work based on a package version" {
            val pkg = Package.EMPTY.copy(
                id = Identifier("Test:::$PKG_VERSION"),

                // Use a non-blank dummy revision to enforce multiple revision candidates being tried.
                vcsProcessed = VcsInfo(VcsType.GIT, REPO_URL, "dummy")
            )

            val workingTree = git.download(pkg, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
        }

        "get only the given path based on a package version" {
            val pkg = Package.EMPTY.copy(
                id = Identifier("Test:::$PKG_VERSION"),

                // Use a non-blank dummy revision to enforce multiple revision candidates being tried.
                vcsProcessed = VcsInfo(VcsType.GIT, REPO_URL, "dummy", path = REPO_PATH_FOR_VERSION)
            )

            val workingTree = git.download(pkg, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
            workingTree.getRootPath().walk().mapNotNullTo(mutableListOf()) { file ->
                file.toRelativeString(outputDir).takeIf { it.isNotEmpty() && !it.startsWith('.') }
            }.shouldContainExactlyInAnyOrder(
                "LICENSE",
                "README.md",
                "specs",
                "specs/dep_graph_spec.js"
            )
        }

        "apply URL replacements recursively" {
            val url = "https://github.com/oss-review-toolkit/ort-test-data-git-submodules.git"
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.GIT, url, "git-protocol-submodule-urls"))

            // Using GitHub with the deprecated "git://" protocol actually hangs instead of failing explicitly.
            val workingTree = shouldCompleteWithin(40, TimeUnit.SECONDS) {
                git.download(pkg, outputDir, allowMovingRevisions = true)
            }

            workingTree.isValid() shouldBe true
            workingTree.getRootPath().walk().onEnter {
                it == outputDir || it.parentFile.resolve(".gitmodules").isFile
            }.mapNotNullTo(mutableListOf()) { file ->
                file.toRelativeString(outputDir).takeIf { it.isNotEmpty() && !it.startsWith('.') }
            }.shouldContainExactlyInAnyOrder(
                "commons-text",
                "test-data-npm",
                "LICENSE",
                "README.md",

                "commons-text/.git",
                "commons-text/.gitignore",
                "commons-text/.travis.yml",
                "commons-text/CONTRIBUTING.md",
                "commons-text/LICENSE.txt",
                "commons-text/NOTICE.txt",
                "commons-text/README.md",
                "commons-text/RELEASE-NOTES.txt",
                "commons-text/checkstyle-suppressions.xml",
                "commons-text/checkstyle.xml",
                "commons-text/license-header.txt",
                "commons-text/pom.xml",
                "commons-text/sb-excludes.xml",

                "test-data-npm/isarray",
                "test-data-npm/long.js",
                "test-data-npm/.git",
                "test-data-npm/.gitignore",
                "test-data-npm/.gitmodules",
                "test-data-npm/LICENSE",
                "test-data-npm/README.md",
                "test-data-npm/package-lock.json",
                "test-data-npm/package.json",

                "test-data-npm/isarray/.git",
                "test-data-npm/isarray/.gitignore",
                "test-data-npm/isarray/.travis.yml",
                "test-data-npm/isarray/LICENSE",
                "test-data-npm/isarray/Makefile",
                "test-data-npm/isarray/README.md",
                "test-data-npm/isarray/component.json",
                "test-data-npm/isarray/index.js",
                "test-data-npm/isarray/package-lock.json",
                "test-data-npm/isarray/package.json",
                "test-data-npm/isarray/test.js",

                "test-data-npm/long.js/.git",
                "test-data-npm/long.js/.gitignore",
                "test-data-npm/long.js/.travis.yml",
                "test-data-npm/long.js/LICENSE",
                "test-data-npm/long.js/README.md",
                "test-data-npm/long.js/bower.json",
                "test-data-npm/long.js/index.js",
                "test-data-npm/long.js/package.json",
                "test-data-npm/long.js/webpack.config.js"
            )
        }
    }
})
