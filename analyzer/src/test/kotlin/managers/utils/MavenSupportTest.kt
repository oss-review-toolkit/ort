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

package org.ossreviewtoolkit.analyzer.managers.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.apache.maven.model.Scm
import org.apache.maven.project.MavenProject

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class MavenSupportTest : WordSpec({
    "parseVcsInfo()" should {
        "handle GitRepo URLs" {
            val mavenProject = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:git-repo:ssh://host.com/project/foo?manifest=path/to/manifest.xml"
                    tag = "v1.2.3"
                }
            }

            MavenSupport.parseVcsInfo(mavenProject) shouldBe VcsInfo(
                type = VcsType.GIT_REPO,
                url = "ssh://host.com/project/foo?manifest=path/to/manifest.xml",
                revision = "v1.2.3"
            )
        }

        "handle deprecated GitRepo URLs" {
            val mavenProject = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:git-repo:ssh://host.com/project/foo?path/to/manifest.xml"
                    tag = "v1.2.3"
                }
            }

            MavenSupport.parseVcsInfo(mavenProject) shouldBe VcsInfo(
                type = VcsType.GIT_REPO,
                url = "ssh://host.com/project/foo?manifest=path/to/manifest.xml",
                revision = "v1.2.3"
            )
        }

        "handle GitHub URLs with missing SCM provider" {
            val httpsProvider = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:https://ben-manes@github.com/ben-manes/caffeine.git"
                    tag = "v2.8.1"
                }
            }
            val gitProvider = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:git://github.com/vigna/fastutil.git"
                }
            }

            MavenSupport.parseVcsInfo(httpsProvider) shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://ben-manes@github.com/ben-manes/caffeine.git",
                revision = "v2.8.1"
            )
            MavenSupport.parseVcsInfo(gitProvider) shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "git://github.com/vigna/fastutil.git",
                revision = ""
            )
        }

        "handle GitHub URLs with the project name as a path prefix" {
            val mavenProject = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:git:git://github.com/netty/netty-tcnative.git/netty-tcnative-boringssl-static"
                }
            }

            MavenSupport.parseVcsInfo(mavenProject) shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "git://github.com/netty/netty-tcnative.git",
                revision = "",
                path = "boringssl-static"
            )
        }
    }
})
