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

import org.ossreviewtoolkit.downloader.VcsHost.BITBUCKET
import org.ossreviewtoolkit.downloader.VcsHost.GITHUB
import org.ossreviewtoolkit.downloader.VcsHost.GITLAB
import org.ossreviewtoolkit.downloader.VcsHost.SOURCEHUT
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.WordSpec

class VcsHostTest : WordSpec({
    "The Bitbucket implementation" should {
        "be able to extract VCS information from a project URL" {
            BITBUCKET.toVcsInfo(
                "https://bitbucket.org/facebook/lz4revlog/src/4c259957d2904604115a02543dc73f5be95761d7/COPYING"
            ) shouldBe
                    VcsInfo(
                        type = VcsType.MERCURIAL,
                        url = "https://bitbucket.org/facebook/lz4revlog",
                        revision = "4c259957d2904604115a02543dc73f5be95761d7",
                        path = "COPYING"
                    )
        }

        "be able to create permalinks from VCS information" {
            val vcsInfo = VcsInfo(
                type = VcsType.MERCURIAL,
                url = "ssh://hg@bitbucket.org/paniq/masagin",
                revision = "23349a9ba924f6341bd7f1ed907b1b413b298342",
                path = "masagin.gpl"
            )

            BITBUCKET.toPermalink(vcsInfo, 5) shouldBe "https://bitbucket.org/paniq/masagin/" +
                    "src/23349a9ba924f6341bd7f1ed907b1b413b298342/masagin.gpl#lines-5"
            BITBUCKET.toPermalink(vcsInfo, 5, 8) shouldBe "https://bitbucket.org/paniq/masagin/" +
                    "src/23349a9ba924f6341bd7f1ed907b1b413b298342/masagin.gpl#lines-5:8"
        }
    }

    "The GitHub implementation" should {
        "be able to extract VCS information from a project URL" {
            GITHUB.toVcsInfo(
                "https://github.com/oss-review-toolkit/ort/tree/da7e3a814fc0e6301bf3ed394eba1a661e4d88d7/README.md"
            ) shouldBe
                    VcsInfo(
                        type = VcsType.GIT,
                        url = "https://github.com/oss-review-toolkit/ort.git",
                        revision = "da7e3a814fc0e6301bf3ed394eba1a661e4d88d7",
                        path = "README.md"
                    )
        }

        "be able to create permalinks from VCS information" {
            val vcsInfo = VcsInfo(
                type = VcsType.GIT,
                url = "git@github.com:oss-review-toolkit/ort.git",
                revision = "4a836c3a6a42d358362fa07b014b7d83572a13ed",
                path = "docs/examples/gradle.ort.yml"
            )

            GITHUB.toPermalink(vcsInfo, 3) shouldBe "https://github.com/oss-review-toolkit/ort/" +
                    "tree/4a836c3a6a42d358362fa07b014b7d83572a13ed/docs/examples/gradle.ort.yml#L3"
            GITHUB.toPermalink(vcsInfo, 3, 5) shouldBe "https://github.com/oss-review-toolkit/ort/" +
                    "tree/4a836c3a6a42d358362fa07b014b7d83572a13ed/docs/examples/gradle.ort.yml#L3-L5"
        }

        "be able to create permalinks to Markdown files" {
            val vcsInfo = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/oss-review-toolkit/ort.git",
                revision = "da7e3a814fc0e6301bf3ed394eba1a661e4d88d7",
                path = "README.md"
            )

            GITHUB.toPermalink(vcsInfo, 27) shouldBe "https://github.com/oss-review-toolkit/ort/" +
                    "blame/da7e3a814fc0e6301bf3ed394eba1a661e4d88d7/README.md#L27"
            GITHUB.toPermalink(vcsInfo, 27, 28) shouldBe "https://github.com/oss-review-toolkit/ort/" +
                    "blame/da7e3a814fc0e6301bf3ed394eba1a661e4d88d7/README.md#L27-L28"
        }
    }

    "The GitLab implementation" should {
        "be able to extract VCS information from a project URL" {
            GITLAB.toVcsInfo(
                "https://gitlab.com/mbunkus/mkvtoolnix/tree/ec80478f87f1941fe52f15c5f4fa7ee6a70d7006/NEWS.md"
            ) shouldBe
                    VcsInfo(
                        type = VcsType.GIT,
                        url = "https://gitlab.com/mbunkus/mkvtoolnix.git",
                        revision = "ec80478f87f1941fe52f15c5f4fa7ee6a70d7006",
                        path = "NEWS.md"
                    )
        }

        "be able to create permalinks from VCS information" {
            val vcsInfo = VcsInfo(
                type = VcsType.GIT,
                url = "https://gitlab.com/mbunkus/mkvtoolnix.git",
                revision = "12542c481ff1e0abcf8d561d6741e561ef5675ca",
                path = "autogen.sh"
            )

            GITLAB.toPermalink(vcsInfo, 7) shouldBe "https://gitlab.com/mbunkus/mkvtoolnix/" +
                    "tree/12542c481ff1e0abcf8d561d6741e561ef5675ca/autogen.sh#L7"
            GITLAB.toPermalink(vcsInfo, 7, 9) shouldBe "https://gitlab.com/mbunkus/mkvtoolnix/" +
                    "tree/12542c481ff1e0abcf8d561d6741e561ef5675ca/autogen.sh#L7-9"
        }

        "be able to create permalinks to Markdown files" {
            val vcsInfo = VcsInfo(
                type = VcsType.GIT,
                url = "https://gitlab.com/mbunkus/mkvtoolnix.git",
                revision = "ec80478f87f1941fe52f15c5f4fa7ee6a70d7006",
                path = "NEWS.md"
            )

            GITLAB.toPermalink(vcsInfo, 5) shouldBe "https://gitlab.com/mbunkus/mkvtoolnix/" +
                    "blame/ec80478f87f1941fe52f15c5f4fa7ee6a70d7006/NEWS.md#L5"
            GITLAB.toPermalink(vcsInfo, 5, 7) shouldBe "https://gitlab.com/mbunkus/mkvtoolnix/" +
                    "blame/ec80478f87f1941fe52f15c5f4fa7ee6a70d7006/NEWS.md#L5-7"
        }
    }

    "The SourceHut implementation" should {
        "be able to extract VCS information from a Git project URL" {
            SOURCEHUT.toVcsInfo(
                "https://git.sr.ht/~ben/web/tree/2c3d173d/pkgs.nix"
            ) shouldBe
                    VcsInfo(
                        type = VcsType.GIT,
                        url = "https://git.sr.ht/~ben/web",
                        revision = "2c3d173d",
                        path = "pkgs.nix"
                    )
        }

        "be able to extract VCS information from a Mercurial project URL" {
            SOURCEHUT.toVcsInfo(
                "https://hg.sr.ht/~duangle/paniq_legacy/browse/f04521a92844/masagin/visual_cues.png"
            ) shouldBe
                    VcsInfo(
                        type = VcsType.MERCURIAL,
                        url = "https://hg.sr.ht/~duangle/paniq_legacy",
                        revision = "f04521a92844",
                        path = "masagin/visual_cues.png"
                    )
        }

        "be able to create permalinks from Git VCS information" {
            val vcsInfo = VcsInfo(
                type = VcsType.GIT,
                url = "https://git.sr.ht/~ben/web",
                revision = "2c3d173d",
                path = "assets/css/main.css"
            )

            SOURCEHUT.toPermalink(vcsInfo, 26) shouldBe
                    "https://git.sr.ht/~ben/web/tree/2c3d173d/assets/css/main.css#L26"
            SOURCEHUT.toPermalink(vcsInfo, 26, 29) shouldBe
                    "https://git.sr.ht/~ben/web/tree/2c3d173d/assets/css/main.css#L26-29"
        }

        "be able to create permalinks from Mercurial VCS information" {
            val vcsInfo = VcsInfo(
                type = VcsType.MERCURIAL,
                url = "https://hg.sr.ht/~duangle/paniq_legacy",
                revision = "f04521a92844",
                path = "masagin/README.txt"
            )

            SOURCEHUT.toPermalink(vcsInfo, 9) shouldBe
                    "https://hg.sr.ht/~duangle/paniq_legacy/browse/f04521a92844/masagin/README.txt#L9"
            // SourceHut does not support an end line in permalinks to Mercural repos.
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
})
