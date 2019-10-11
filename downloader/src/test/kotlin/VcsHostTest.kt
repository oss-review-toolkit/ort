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

package com.here.ort.downloader

import com.here.ort.downloader.VcsHost.BITBUCKET
import com.here.ort.downloader.VcsHost.GITHUB
import com.here.ort.downloader.VcsHost.GITLAB
import com.here.ort.model.VcsInfo
import com.here.ort.model.VcsType

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class VcsHostTest : WordSpec({
    "Bitbucket" should {
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

    "GitHub" should {
        "be able to extract VCS information from a project URL" {
            GITHUB.toVcsInfo(
                "https://github.com/heremaps/oss-review-toolkit/blob/da7e3a814fc0e6301bf3ed394eba1a661e4d88d7/README.md"
            ) shouldBe
                    VcsInfo(
                        type = VcsType.GIT,
                        url = "https://github.com/heremaps/oss-review-toolkit.git",
                        revision = "da7e3a814fc0e6301bf3ed394eba1a661e4d88d7",
                        path = "README.md"
                    )
        }

        "be able to create permalinks from VCS information" {
            val vcsInfo = VcsInfo(
                type = VcsType.GIT,
                url = "git@github.com:heremaps/oss-review-toolkit.git",
                revision = "4a836c3a6a42d358362fa07b014b7d83572a13ed",
                path = "docs/examples/gradle.ort.yml"
            )

            GITHUB.toPermalink(vcsInfo, 3) shouldBe "https://github.com/heremaps/oss-review-toolkit/" +
                    "blob/4a836c3a6a42d358362fa07b014b7d83572a13ed/docs/examples/gradle.ort.yml#L3"
            GITHUB.toPermalink(vcsInfo, 3, 5) shouldBe "https://github.com/heremaps/oss-review-toolkit/" +
                    "blob/4a836c3a6a42d358362fa07b014b7d83572a13ed/docs/examples/gradle.ort.yml#L3-L5"
        }

        "be able to create permalinks to Markdown files" {
            val vcsInfo = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/heremaps/oss-review-toolkit.git",
                revision = "da7e3a814fc0e6301bf3ed394eba1a661e4d88d7",
                path = "README.md"
            )

            GITHUB.toPermalink(vcsInfo, 27) shouldBe "https://github.com/heremaps/oss-review-toolkit/" +
                    "blame/da7e3a814fc0e6301bf3ed394eba1a661e4d88d7/README.md#L27"
            GITHUB.toPermalink(vcsInfo, 27, 28) shouldBe "https://github.com/heremaps/oss-review-toolkit/" +
                    "blame/da7e3a814fc0e6301bf3ed394eba1a661e4d88d7/README.md#L27-L28"
        }
    }

    "GitLab" should {
        "be able to extract VCS information from a project URL" {
            GITLAB.toVcsInfo(
                "https://gitlab.com/mbunkus/mkvtoolnix/blob/ec80478f87f1941fe52f15c5f4fa7ee6a70d7006/NEWS.md"
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
                    "blob/12542c481ff1e0abcf8d561d6741e561ef5675ca/autogen.sh#L7"
            GITLAB.toPermalink(vcsInfo, 7, 9) shouldBe "https://gitlab.com/mbunkus/mkvtoolnix/" +
                    "blob/12542c481ff1e0abcf8d561d6741e561ef5675ca/autogen.sh#L7-9"
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
})
