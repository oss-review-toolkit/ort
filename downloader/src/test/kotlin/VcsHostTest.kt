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
    }
})
