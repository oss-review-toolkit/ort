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

package com.here.ort.downloader

import com.here.ort.model.VcsInfo
import com.here.ort.model.VcsType

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

    "splitUrl" should {
        "split paths from a URL to a Git repository" {
            val actual = VersionControlSystem.splitUrl(
                "https://git-wip-us.apache.org/repos/asf/zeppelin.git"
            )
            val expected = VcsInfo(
                type = VcsType.GIT,
                url = "https://git-wip-us.apache.org/repos/asf/zeppelin.git",
                revision = "",
                path = ""
            )
            actual shouldBe expected
        }

        "split paths from a URL to a Git repository with path" {
            val actual = VersionControlSystem.splitUrl(
                "https://git-wip-us.apache.org/repos/asf/zeppelin.git/zeppelin-interpreter"
            )
            val expected = VcsInfo(
                type = VcsType.GIT,
                url = "https://git-wip-us.apache.org/repos/asf/zeppelin.git",
                revision = "",
                path = "zeppelin-interpreter"
            )
            actual shouldBe expected
        }

        "split the revision from an NPM URL to a Git repository" {
            val actual = VersionControlSystem.splitUrl(
                "git+ssh://sub.domain.com:42/foo-bar#b3b5b3c60dcdc39347b23cf94ab8f577239b7df3"
            )
            val expected = VcsInfo(
                type = VcsType.GIT,
                url = "ssh://sub.domain.com:42/foo-bar",
                revision = "b3b5b3c60dcdc39347b23cf94ab8f577239b7df3",
                path = ""
            )
            actual shouldBe expected
        }

        "split the revision from a NPM URL to a GitHub repository" {
            val actual = VersionControlSystem.splitUrl(
                "https://github.com/mochajs/mocha.git#5bd33a0ba201d227159759e8ced86756595b0c54"
            )
            val expected = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/mochajs/mocha.git",
                revision = "5bd33a0ba201d227159759e8ced86756595b0c54",
                path = ""
            )
            actual shouldBe expected
        }

        "separate an SVN branch into the revision" {
            val actual = VersionControlSystem.splitUrl(
                "http://svn.osdn.net/svnroot/tortoisesvn/branches/1.13.x"
            )
            val expected = VcsInfo(
                type = VcsType.SUBVERSION,
                url = "http://svn.osdn.net/svnroot/tortoisesvn",
                revision = "branches/1.13.x",
                path = ""
            )
            actual shouldBe expected
        }

        "separate branch and path from an SVN URL" {
            val actual = VersionControlSystem.splitUrl(
                "http://svn.osdn.net/svnroot/tortoisesvn/branches/1.13.x/src/gpl.txt"
            )
            val expected = VcsInfo(
                type = VcsType.SUBVERSION,
                url = "http://svn.osdn.net/svnroot/tortoisesvn",
                revision = "branches/1.13.x",
                path = "src/gpl.txt"
            )
            actual shouldBe expected
        }

        "separate an SVN tag into the revision" {
            val actual = VersionControlSystem.splitUrl(
                "http://svn.terracotta.org/svn/ehcache/tags/ehcache-parent-2.21"
            )
            val expected = VcsInfo(
                type = VcsType.SUBVERSION,
                url = "http://svn.terracotta.org/svn/ehcache",
                revision = "tags/ehcache-parent-2.21",
                path = ""
            )
            actual shouldBe expected
        }

        "separate tag and path from an SVN URL" {
            val actual = VersionControlSystem.splitUrl(
                "http://svn.terracotta.org/svn/ehcache/tags/ehcache-parent-2.21/pom.xml"
            )
            val expected = VcsInfo(
                type = VcsType.SUBVERSION,
                url = "http://svn.terracotta.org/svn/ehcache",
                revision = "tags/ehcache-parent-2.21",
                path = "pom.xml"
            )
            actual shouldBe expected
        }
    }
})
