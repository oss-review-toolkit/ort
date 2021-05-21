/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.downloader

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.net.URI

import org.ossreviewtoolkit.downloader.VcsHost.BITBUCKET
import org.ossreviewtoolkit.downloader.VcsHost.GITHUB
import org.ossreviewtoolkit.downloader.VcsHost.GITLAB
import org.ossreviewtoolkit.downloader.VcsHost.SOURCEHUT
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class VcsHostTest : WordSpec({
    "The Bitbucket implementation" should {
        val projectUrl = "https://bitbucket.org/yevster/spdxtraxample/" +
                "src/287aebca5e7ff4167af1fb648640dcdbdf4ec666/LICENSE.txt"
        val vcsInfo = VcsInfo(
            type = VcsType.GIT,
            url = "https://bitbucket.org/yevster/spdxtraxample.git",
            revision = "287aebca5e7ff4167af1fb648640dcdbdf4ec666",
            path = "LICENSE.txt"
        )

        "correctly get the user or organization name" {
            BITBUCKET.getUserOrOrganization(projectUrl) shouldBe "yevster"
        }

        "correctly get the project name" {
            BITBUCKET.getProject(projectUrl) shouldBe "spdxtraxample"
        }

        "be able to extract VCS information from a project URL" {
            BITBUCKET.toVcsInfo(projectUrl) shouldBe vcsInfo
        }

        "be able to create permalinks from VCS information" {
            BITBUCKET.toPermalink(vcsInfo, 1) shouldBe "https://bitbucket.org/yevster/spdxtraxample/" +
                    "src/287aebca5e7ff4167af1fb648640dcdbdf4ec666/LICENSE.txt#lines-1"
            BITBUCKET.toPermalink(vcsInfo, 4, 8) shouldBe "https://bitbucket.org/yevster/spdxtraxample/" +
                    "src/287aebca5e7ff4167af1fb648640dcdbdf4ec666/LICENSE.txt#lines-4:8"
        }

        "be able to create source archive links from project URLs" {
            BITBUCKET.toArchiveDownloadUrl(vcsInfo) shouldBe
                    "https://bitbucket.org/yevster/spdxtraxample/get/287aebca5e7ff4167af1fb648640dcdbdf4ec666.tar.gz"
        }
    }

    "The GitHub implementation" should {
        val projectUrl = "https://github.com/oss-review-toolkit/ort/" +
                "blob/da7e3a814fc0e6301bf3ed394eba1a661e4d88d7/README.md"
        val vcsInfo = VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/oss-review-toolkit/ort.git",
            revision = "da7e3a814fc0e6301bf3ed394eba1a661e4d88d7",
            path = "README.md"
        )

        "correctly get the user or organization name" {
            GITHUB.getUserOrOrganization(projectUrl) shouldBe "oss-review-toolkit"
        }

        "correctly get the project name" {
            GITHUB.getProject(projectUrl) shouldBe "ort"
        }

        "be able to extract VCS information from a project URL" {
            GITHUB.toVcsInfo(projectUrl) shouldBe vcsInfo
        }

        "be able to create permalinks from VCS information" {
            val scpVcsInfo = VcsInfo(
                type = VcsType.GIT,
                url = "git@github.com:oss-review-toolkit/ort.git",
                revision = "4a836c3a6a42d358362fa07b014b7d83572a13ed",
                path = "docs/examples/gradle.ort.yml"
            )

            GITHUB.toPermalink(scpVcsInfo, 3) shouldBe "https://github.com/oss-review-toolkit/ort/" +
                    "tree/4a836c3a6a42d358362fa07b014b7d83572a13ed/docs/examples/gradle.ort.yml#L3"
            GITHUB.toPermalink(scpVcsInfo, 3, 5) shouldBe "https://github.com/oss-review-toolkit/ort/" +
                    "tree/4a836c3a6a42d358362fa07b014b7d83572a13ed/docs/examples/gradle.ort.yml#L3-L5"
        }

        "be able to create permalinks to Markdown files" {
            GITHUB.toPermalink(vcsInfo, 27) shouldBe "https://github.com/oss-review-toolkit/ort/" +
                    "blame/da7e3a814fc0e6301bf3ed394eba1a661e4d88d7/README.md#L27"
            GITHUB.toPermalink(vcsInfo, 27, 28) shouldBe "https://github.com/oss-review-toolkit/ort/" +
                    "blame/da7e3a814fc0e6301bf3ed394eba1a661e4d88d7/README.md#L27-L28"
        }

        "be able to create source archive links from project URLs" {
            GITHUB.toArchiveDownloadUrl(vcsInfo) shouldBe
                    "https://github.com/oss-review-toolkit/ort/archive/da7e3a814fc0e6301bf3ed394eba1a661e4d88d7.tar.gz"
        }
    }

    "The GitLab implementation" should {
        val projectUrl = "https://gitlab.com/mbunkus/mkvtoolnix/-/blob/ec80478f87f1941fe52f15c5f4fa7ee6a70d7006/NEWS.md"
        val vcsInfo = VcsInfo(
            type = VcsType.GIT,
            url = "https://gitlab.com/mbunkus/mkvtoolnix.git",
            revision = "ec80478f87f1941fe52f15c5f4fa7ee6a70d7006",
            path = "NEWS.md"
        )

        "correctly get the user or organization name" {
            GITLAB.getUserOrOrganization(projectUrl) shouldBe "mbunkus"
        }

        "correctly get the project name" {
            GITLAB.getProject(projectUrl) shouldBe "mkvtoolnix"
        }

        "be able to extract VCS information from a project URL" {
            GITLAB.toVcsInfo(projectUrl) shouldBe vcsInfo
        }

        "be able to create permalinks from VCS information" {
            val scpVcsInfo = VcsInfo(
                type = VcsType.GIT,
                url = "git@gitlab.com:mbunkus/mkvtoolnix.git",
                revision = "12542c481ff1e0abcf8d561d6741e561ef5675ca",
                path = "autogen.sh"
            )

            GITLAB.toPermalink(scpVcsInfo, 7) shouldBe "https://gitlab.com/mbunkus/mkvtoolnix/" +
                    "tree/12542c481ff1e0abcf8d561d6741e561ef5675ca/autogen.sh#L7"
            GITLAB.toPermalink(scpVcsInfo, 7, 9) shouldBe "https://gitlab.com/mbunkus/mkvtoolnix/" +
                    "tree/12542c481ff1e0abcf8d561d6741e561ef5675ca/autogen.sh#L7-9"
        }

        "be able to create permalinks to Markdown files" {
            GITLAB.toPermalink(vcsInfo, 5) shouldBe "https://gitlab.com/mbunkus/mkvtoolnix/" +
                    "blame/ec80478f87f1941fe52f15c5f4fa7ee6a70d7006/NEWS.md#L5"
            GITLAB.toPermalink(vcsInfo, 5, 7) shouldBe "https://gitlab.com/mbunkus/mkvtoolnix/" +
                    "blame/ec80478f87f1941fe52f15c5f4fa7ee6a70d7006/NEWS.md#L5-7"
        }

        "be able to create source archive links from project URLs" {
            GITLAB.toArchiveDownloadUrl(vcsInfo) shouldBe
                    "https://gitlab.com/mbunkus/mkvtoolnix/-/archive/ec80478f87f1941fe52f15c5f4fa7ee6a70d7006/" +
                    "mkvtoolnix-ec80478f87f1941fe52f15c5f4fa7ee6a70d7006.tar.gz"
        }
    }

    "The SourceHut implementation" should {
        val gitProjectUrl = "https://git.sr.ht/~ben/web/tree/2c3d173d/assets/css/main.css"
        val gitVcsInfo = VcsInfo(
            type = VcsType.GIT,
            url = "https://git.sr.ht/~ben/web",
            revision = "2c3d173d",
            path = "assets/css/main.css"
        )

        val hgProjectUrl = "https://hg.sr.ht/~duangle/paniq_legacy/browse/f04521a92844/masagin/README.txt"
        val hgVcsInfo = VcsInfo(
            type = VcsType.MERCURIAL,
            url = "https://hg.sr.ht/~duangle/paniq_legacy",
            revision = "f04521a92844",
            path = "masagin/README.txt"
        )

        "correctly get the user or organization name" {
            SOURCEHUT.getUserOrOrganization(gitProjectUrl) shouldBe "ben"
        }

        "correctly get the project name" {
            SOURCEHUT.getProject(gitProjectUrl) shouldBe "web"
        }

        "be able to extract VCS information from a Git project URL" {
            SOURCEHUT.toVcsInfo(gitProjectUrl) shouldBe gitVcsInfo
        }

        "be able to extract VCS information from a Mercurial project URL" {
            SOURCEHUT.toVcsInfo(hgProjectUrl) shouldBe hgVcsInfo
        }

        "be able to create permalinks from Git VCS information" {
            SOURCEHUT.toPermalink(gitVcsInfo, 26) shouldBe
                    "https://git.sr.ht/~ben/web/tree/2c3d173d/assets/css/main.css#L26"
            SOURCEHUT.toPermalink(gitVcsInfo, 26, 29) shouldBe
                    "https://git.sr.ht/~ben/web/tree/2c3d173d/assets/css/main.css#L26-29"
        }

        "be able to create permalinks from Mercurial VCS information" {
            SOURCEHUT.toPermalink(hgVcsInfo, 9) shouldBe
                    "https://hg.sr.ht/~duangle/paniq_legacy/browse/f04521a92844/masagin/README.txt#L9"
            // SourceHut does not support an end line in permalinks to Mercurial repos.
        }

        "be able to create source archive links from project URLs" {
            SOURCEHUT.toArchiveDownloadUrl(gitVcsInfo) shouldBe "https://git.sr.ht/~ben/web/archive/2c3d173d.tar.gz"
        }
    }

    "The generic implementation" should {
        "split paths from a URL to a Git repository" {
            val actual = VcsHost.toVcsInfo(
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
            val actual = VcsHost.toVcsInfo(
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
            val actual = VcsHost.toVcsInfo(
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
            val actual = VcsHost.toVcsInfo(
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
            val actual = VcsHost.toVcsInfo(
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
            val actual = VcsHost.toVcsInfo(
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
            val actual = VcsHost.toVcsInfo(
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
            val actual = VcsHost.toVcsInfo(
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

    "The conversion from URI to VcsHost" should {
        "convert a Github URI" {
            VcsHost.toVcsHost(URI("https://github.com/oss-review-toolkit/ort")) shouldBe GITHUB
        }

        "convert a Gitlab URI" {
            VcsHost.toVcsHost(URI("https://gitlab.com/gitlab-org/gitlab")) shouldBe GITLAB
        }

        "convert a Bitbucket URI" {
            VcsHost.toVcsHost(URI("https://bitbucket.org/yevster/spdxtraxample")) shouldBe BITBUCKET
        }

        "convert a SourceHut git URI" {
            VcsHost.toVcsHost(URI("https://git.sr.ht/~sircmpwn/sourcehut.org")) shouldBe SOURCEHUT
        }

        "convert a SourceHut hg URI" {
            VcsHost.toVcsHost(URI("https://hg.sr.ht/~sircmpwn/invertbucket")) shouldBe SOURCEHUT
        }

        "handle an unknown URI" {
            VcsHost.toVcsHost(URI("https://host.tld/path/to/repo")) should beNull()
        }
    }
})
