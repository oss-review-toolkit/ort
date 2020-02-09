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

package com.here.ort.analyzer.managers.utils

import com.here.ort.model.VcsInfo
import com.here.ort.model.VcsType
import com.here.ort.model.yamlMapper

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import org.apache.maven.model.Scm
import org.apache.maven.project.MavenProject
import org.apache.maven.settings.Proxy

class MavenSupportTest : WordSpec({
    "parseVcsInfo()" should {
        "handle GitRepo URLs" {
            val mavenProject = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:git-repo:ssh://host.com/project/foo?path/to/manifest.xml"
                    tag = "v1.2.3"
                }
            }

            MavenSupport.parseVcsInfo(mavenProject) shouldBe VcsInfo(
                type = VcsType.GIT_REPO,
                url = "ssh://host.com/project/foo",
                revision = "v1.2.3",
                path = "path/to/manifest.xml"
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
    }

    "createProxyFromUrl" should {
        "correctly convert URLs" {
            val actualProxy = MavenSupport.createProxyFromUrl("https://host:23")
            val expectedProxy = Proxy().apply {
                protocol = "https"
                host = "host"
                port = 23
            }

            yamlMapper.writeValueAsString(actualProxy) shouldBe yamlMapper.writeValueAsString(expectedProxy)
        }

        "correctly convert URLs without a port" {
            val actualProxy = MavenSupport.createProxyFromUrl("http://host")
            val expectedProxy = Proxy().apply {
                protocol = "http"
                host = "host"
            }

            yamlMapper.writeValueAsString(actualProxy) shouldBe yamlMapper.writeValueAsString(expectedProxy)
        }

        "correctly convert URLs with user info" {
            val actualProxy = MavenSupport.createProxyFromUrl("https://user:pass@host:42")
            val expectedProxy = Proxy().apply {
                protocol = "https"
                username = "user"
                password = "pass"
                host = "host"
                port = 42
            }

            yamlMapper.writeValueAsString(actualProxy) shouldBe yamlMapper.writeValueAsString(expectedProxy)
        }
    }
})
