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

package com.here.ort.downloader

import com.here.ort.downloader.vcs.Mercurial
import com.here.ort.model.VcsInfo

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class VersionControlSystemTest : WordSpec({
    val vcsRoot = File("..").absoluteFile.normalize()
    val relProjDir = File("src/test")
    val absProjDir = relProjDir.absoluteFile

    "For an absolute working directory, getPathToRoot()" should {
        val absVcsDir = VersionControlSystem.forDirectory(absProjDir)!!

        "work if given absolute paths" {
            absVcsDir.getPathToRoot(vcsRoot) shouldBe ""
            absVcsDir.getPathToRoot(vcsRoot.resolve("downloader/src")) shouldBe "downloader/src"
            absVcsDir.getPathToRoot(absProjDir.resolve("kotlin")) shouldBe "downloader/src/test/kotlin"
        }

        "work if given relative paths" {
            absVcsDir.getPathToRoot(File(".")) shouldBe "downloader"
            absVcsDir.getPathToRoot(File("..")) shouldBe ""
            absVcsDir.getPathToRoot(File("src/test/kotlin")) shouldBe "downloader/src/test/kotlin"
        }
    }

    "For a relative working directory, getPathToRoot()" should {
        val relVcsDir = VersionControlSystem.forDirectory(relProjDir)!!

        "work if given absolute paths" {
            relVcsDir.getPathToRoot(vcsRoot) shouldBe ""
            relVcsDir.getPathToRoot(vcsRoot.resolve("downloader/src")) shouldBe "downloader/src"
            relVcsDir.getPathToRoot(absProjDir.resolve("kotlin")) shouldBe "downloader/src/test/kotlin"
        }

        "work if given relative paths" {
            relVcsDir.getPathToRoot(relProjDir) shouldBe "downloader/src/test"
            relVcsDir.getPathToRoot(File("..")) shouldBe ""
            relVcsDir.getPathToRoot(File("src/test/kotlin")) shouldBe "downloader/src/test/kotlin"
        }
    }

    "splitUrl for Bitbucket" should {
        "not modify URLs without a path".config(enabled = Mercurial.isInPath()) {
            val actual = VersionControlSystem.splitUrl(
                    "https://bitbucket.org/paniq/masagin"
            )
            val expected = VcsInfo(
                    type = "Mercurial",
                    url = "https://bitbucket.org/paniq/masagin",
                    revision = ""
            )
            actual shouldBe expected
        }

        "split tree URLs" {
            val actual = VersionControlSystem.splitUrl(
                    "https://bitbucket.org/yevster/spdxtraxample/src/287aebc/src/java/com/yevster/example/?at=master"
            )
            val expected = VcsInfo(
                    type = "Git",
                    url = "https://bitbucket.org/yevster/spdxtraxample.git",
                    revision = "287aebc",
                    path = "src/java/com/yevster/example/"
            )
            actual shouldBe expected
        }

        "split blob URLs" {
            val actual = VersionControlSystem.splitUrl(
                    "https://bitbucket.org/yevster/spdxtraxample/src/287aebc/README.md?at=master"
            )
            val expected = VcsInfo(
                    type = "Git",
                    url = "https://bitbucket.org/yevster/spdxtraxample.git",
                    revision = "287aebc",
                    path = "README.md"
            )
            actual shouldBe expected
        }
    }

    "splitUrl for GitHub" should {
        "not modify URLs without a path" {
            val actual = VersionControlSystem.splitUrl(
                    "https://github.com/heremaps/oss-review-toolkit.git"
            )
            val expected = VcsInfo(
                    type = "git",
                    url = "https://github.com/heremaps/oss-review-toolkit.git",
                    revision = ""
            )
            actual shouldBe expected
        }

        "not fail for a user called blob or a project called tree" {
            val actual = VersionControlSystem.splitUrl(
                    "https://github.com/blob/tree.git"
            )
            val expected = VcsInfo(
                    type = "git",
                    url = "https://github.com/blob/tree.git",
                    revision = ""
            )
            actual shouldBe expected
        }

        "split tree URLs" {
            val actual = VersionControlSystem.splitUrl(
                    "https://github.com/babel/babel/tree/master/packages/babel-code-frame.git"
            )
            val expected = VcsInfo(
                    type = "git",
                    url = "https://github.com/babel/babel.git",
                    revision = "master",
                    path = "packages/babel-code-frame"
            )
            actual shouldBe expected
        }

        "split blob URLs" {
            val actual = VersionControlSystem.splitUrl(
                    "https://github.com/crypto-browserify/crypto-browserify/blob/6aebafa/test/create-hmac.js"
            )
            val expected = VcsInfo(
                    type = "git",
                    url = "https://github.com/crypto-browserify/crypto-browserify.git",
                    revision = "6aebafa",
                    path = "test/create-hmac.js"
            )
            actual shouldBe expected
        }

        "split extra path components" {
            val actual = VersionControlSystem.splitUrl(
                    "ssh://git@github.com/EsotericSoftware/kryo.git/kryo-shaded"
            )
            val expected = VcsInfo(
                    type = "git",
                    url = "ssh://git@github.com/EsotericSoftware/kryo.git",
                    revision = "",
                    path = "kryo-shaded"
            )
            actual shouldBe expected
        }
    }

    "splitUrl for GitLab" should {
        "not modify URLs without a path" {
            val actual = VersionControlSystem.splitUrl(
                    "https://gitlab.com/rich-harris/rollup-plugin-buble.git"
            )
            val expected = VcsInfo(
                    type = "git",
                    url = "https://gitlab.com/rich-harris/rollup-plugin-buble.git",
                    revision = ""
            )
            actual shouldBe expected
        }

        "split tree URLs" {
            val actual = VersionControlSystem.splitUrl(
                    "https://gitlab.com/Rich-Harris/rollup-plugin-buble/tree/master/src"
            )
            val expected = VcsInfo(
                    type = "git",
                    url = "https://gitlab.com/Rich-Harris/rollup-plugin-buble.git",
                    revision = "master",
                    path = "src"
            )
            actual shouldBe expected
        }

        "split blob URLs" {
            val actual = VersionControlSystem.splitUrl(
                    "https://gitlab.com/Rich-Harris/rollup-plugin-buble/blob/v0.15.0/README.md"
            )
            val expected = VcsInfo(
                    type = "git",
                    url = "https://gitlab.com/Rich-Harris/rollup-plugin-buble.git",
                    revision = "v0.15.0",
                    path = "README.md"
            )
            actual shouldBe expected
        }
    }
})
