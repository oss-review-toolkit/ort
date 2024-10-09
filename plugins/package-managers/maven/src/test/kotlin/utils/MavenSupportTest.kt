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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

import org.apache.maven.artifact.repository.Authentication
import org.apache.maven.bridge.MavenRepositorySystem
import org.apache.maven.model.Scm
import org.apache.maven.project.MavenProject
import org.apache.maven.repository.Proxy as MavenProxy

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.util.repository.AuthenticationBuilder

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.MavenSupport.Companion.toArtifactRepository

class MavenSupportTest : WordSpec({
    "getOriginalScm()" should {
        "return the parent's SCM connection if the child SCM uses the parent's SCM connection implicitly" {
            val mavenProject = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:git:https://github.com/spring-projects/spring-boot.git/childArtifactName"
                    url = "https://github.com/oss-review-toolkit/correctUrl"
                }

                parent = MavenProject().apply {
                    scm = Scm().apply {
                        connection = "scm:git:https://github.com/spring-projects/spring-boot.git"
                        url = "https://github.com/spring-projects/spring-boot"
                    }
                }
            }

            MavenSupport.getOriginalScm(mavenProject)?.connection shouldBe
                "scm:git:https://github.com/spring-projects/spring-boot.git"
            MavenSupport.getOriginalScm(mavenProject)?.url shouldBe "https://github.com/oss-review-toolkit/correctUrl"
        }

        "return the parent's SCM URL if the child SCM uses the parent's SCM URL implicitly" {
            val mavenProject = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:git:https://github.com/oss-review-toolkit/childConnection.git"
                    url = "https://github.com/spring-projects/spring-boot/childArtifactName"
                }

                parent = MavenProject().apply {
                    scm = Scm().apply {
                        connection = "scm:git:https://github.com/oss-review-toolkit/parentConnection.git"
                        url = "https://github.com/spring-projects/spring-boot"
                    }
                }
            }

            MavenSupport.getOriginalScm(mavenProject)?.connection shouldBe
                "scm:git:https://github.com/oss-review-toolkit/childConnection.git"
            MavenSupport.getOriginalScm(mavenProject)?.url shouldBe "https://github.com/spring-projects/spring-boot"
        }
    }

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

        "handle GitHub URLs with the project name as a directory prefix" {
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

        "handle GitHub URLs with the project name as a deeper path prefix" {
            val mavenProject = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:git:https://github.com/spring-projects/spring-modulith/" +
                        "spring-modulith-starters/spring-modulith-starter-mongodb"
                }
            }

            MavenSupport.parseVcsInfo(mavenProject) shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/spring-projects/spring-modulith.git",
                revision = "",
                path = "spring-modulith-starters/spring-modulith-starter-mongodb"
            )
        }

        "handle GitHub URLs with double 'git:' prefix" {
            val mavenProject = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:git:git:github.com/MarkusAmshove/Kluent.git"
                }
            }

            MavenSupport.parseVcsInfo(mavenProject) shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "github.com/MarkusAmshove/Kluent.git",
                revision = "",
                path = ""
            )
        }

        "handle GitHub URLs with missing 'git:' provider" {
            val mavenProject = MavenProject().apply {
                scm = Scm().apply {
                    connection = "scm:git@github.com/Yalantis/uCrop.git"
                }
            }

            MavenSupport.parseVcsInfo(mavenProject) shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/Yalantis/uCrop.git",
                revision = "",
                path = ""
            )
        }
    }

    "parseChecksum()" should {
        "return NONE for an empty string" {
            MavenSupport.parseChecksum("", "SHA1") shouldBe Hash.NONE
        }

        "return the first matching hash" {
            MavenSupport.parseChecksum(
                checksum = "868c0792233fc78d8c9bac29ac79ade988301318 7de43522ca1a2a65d7c3b9eacb802a51745b245c",
                algorithm = "SHA1"
            ) shouldBe Hash("868c0792233fc78d8c9bac29ac79ade988301318", "SHA1")
        }

        "ignore prefixes and suffixes" {
            MavenSupport.parseChecksum(
                checksum = "prefix 868c0792233fc78d8c9bac29ac79ade988301318 suffix",
                algorithm = "SHA1"
            ) shouldBe Hash("868c0792233fc78d8c9bac29ac79ade988301318", "SHA1")
        }
    }

    @Suppress("DEPRECATION") // For deprecated ArtifactRepository interface.
    "toArtifactRepository()" should {
        "create a plain artifact repository from a remote repository" {
            val repositoryId = "aTestRepository"
            val repositoryUrl = "https://example.com/repo"
            val repository = RemoteRepository.Builder("ignoredId", null, repositoryUrl).build()

            val session = mockk<RepositorySystemSession>()
            val artifactRepository = mockk<org.apache.maven.artifact.repository.ArtifactRepository>()
            val repositorySystem = mockk<MavenRepositorySystem> {
                every {
                    createRepository(repositoryUrl, repositoryId, true, null, true, null, null)
                } returns artifactRepository
            }

            repository.toArtifactRepository(session, repositorySystem, repositoryId) shouldBe artifactRepository
        }

        "create an artifact repository with a configured proxy" {
            val repository = RemoteRepository.Builder("someId", "someType", "https://example.com/repo")
                .setProxy(Proxy("http", "proxy.example.com", 8080))
                .build()

            val session = mockk<RepositorySystemSession>()
            val artifactRepository = mockk<org.apache.maven.artifact.repository.ArtifactRepository> {
                every { proxy = any() } just runs
            }

            val repositorySystem = mockk<MavenRepositorySystem> {
                every {
                    createRepository(any(), any(), true, null, true, null, null)
                } returns artifactRepository
            }

            repository.toArtifactRepository(session, repositorySystem, "id") shouldBe artifactRepository

            val slotProxy = slot<MavenProxy>()
            verify {
                artifactRepository.proxy = capture(slotProxy)
            }

            with(slotProxy.captured) {
                host shouldBe "proxy.example.com"
                port shouldBe 8080
                protocol shouldBe "http"
            }
        }

        "create an artifact repository with authentication" {
            val repository = RemoteRepository.Builder("someId", "someType", "https://example.com/repo")
                .setAuthentication(
                    AuthenticationBuilder()
                        .addUsername("scott")
                        .addPassword("tiger".toCharArray())
                        .addPrivateKey("privateKeyPath", "passphrase")
                        .build()
                ).build()

            val session = mockk<RepositorySystemSession>()
            val artifactRepository = mockk<org.apache.maven.artifact.repository.ArtifactRepository> {
                every { authentication = any() } just runs
            }

            val repositorySystem = mockk<MavenRepositorySystem> {
                every {
                    createRepository(any(), any(), true, null, true, null, null)
                } returns artifactRepository
            }

            repository.toArtifactRepository(session, repositorySystem, "id") shouldBe artifactRepository

            val slotAuth = slot<Authentication>()
            verify {
                artifactRepository.authentication = capture(slotAuth)
            }

            with(slotAuth.captured) {
                username shouldBe "scott"
                password shouldBe "tiger"
                privateKey shouldBe "privateKeyPath"
                passphrase shouldBe "passphrase"
            }
        }

        "create an artifact repository with a configured proxy that requires authentication" {
            val proxyAuth = AuthenticationBuilder()
                .addUsername("proxyUser")
                .addPassword("proxyPassword".toCharArray())
                .build()
            val repository = RemoteRepository.Builder("someId", "someType", "https://example.com/repo")
                .setProxy(Proxy("http", "proxy.example.com", 8080, proxyAuth))
                .build()

            val session = mockk<RepositorySystemSession>()
            val artifactRepository = mockk<org.apache.maven.artifact.repository.ArtifactRepository> {
                every { proxy = any() } just runs
            }

            val repositorySystem = mockk<MavenRepositorySystem> {
                every {
                    createRepository(any(), any(), true, null, true, null, null)
                } returns artifactRepository
            }

            repository.toArtifactRepository(session, repositorySystem, "id") shouldBe artifactRepository

            val slotProxy = slot<MavenProxy>()
            verify {
                artifactRepository.proxy = capture(slotProxy)
            }

            with(slotProxy.captured) {
                userName shouldBe "proxyUser"
                password shouldBe "proxyPassword"
            }
        }
    }
})
