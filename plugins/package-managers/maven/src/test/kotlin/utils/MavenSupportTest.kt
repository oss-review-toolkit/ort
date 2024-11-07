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
import org.apache.maven.repository.Proxy as MavenProxy

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.Proxy
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.util.repository.AuthenticationBuilder

class MavenSupportTest : WordSpec({
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
