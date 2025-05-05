/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.tycho

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.utils.ort.HttpDownloadError

class P2RepositoryContentLoaderTest : WordSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory(TEST_FILES_ROOT)
    )

    beforeSpec {
        server.start()
    }

    afterSpec {
        server.stop()
    }

    beforeEach {
        server.resetAll()
    }

    "loadRepositoryContent()" should {
        "extract the content from an artifacts.xml file" {
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/artifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("artifacts1.xml")
                    )
            )

            P2RepositoryContentLoader.loadRepositoryContent(tempdir(), server.repositoryUrl())
                .shouldBeSuccess { content ->
                    content.baseUrl shouldBe server.repositoryUrl()
                    content.artifacts shouldHaveSize 2
                    content.artifacts[P2Identifier("org.apache.commons.io:2.8.0.v20210415-0900")] shouldBe Hash(
                        "a58e34c958bcf1704e744dee3793484164ba591e82a32b67fc87414426327e85",
                        HashAlgorithm.SHA256
                    )
                    content.artifacts[P2Identifier("org.apache.commons.codec:1.14.0.v20200818-1422")] shouldBe Hash(
                        "5e8271297be763139bc1189b54166424",
                        HashAlgorithm.MD5
                    )

                    content.childRepositories should beEmpty()
                }
        }

        "extract the content from an artifacts.jar file" {
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/artifacts.jar"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("artifacts.jar")
                    )
            )

            P2RepositoryContentLoader.loadRepositoryContent(tempdir(), server.repositoryUrl())
                .shouldBeSuccess { content ->
                    content.baseUrl shouldBe server.repositoryUrl()
                    content.artifacts shouldHaveSize 2
                    content.artifacts[P2Identifier("org.apache.commons.io:2.8.0.v20210415-0900")] shouldBe Hash(
                        "a58e34c958bcf1704e744dee3793484164ba591e82a32b67fc87414426327e85",
                        HashAlgorithm.SHA256
                    )
                    content.artifacts[P2Identifier("org.apache.commons.codec:1.14.0.v20200818-1422")] shouldBe Hash(
                        "5e8271297be763139bc1189b54166424",
                        HashAlgorithm.MD5
                    )

                    content.childRepositories should beEmpty()
                }
        }

        "return an empty content object if no artifact documents can be found" {
            P2RepositoryContentLoader.loadRepositoryContent(tempdir(), server.repositoryUrl())
                .shouldBeSuccess { content ->
                    content.baseUrl shouldBe server.repositoryUrl()
                    content.artifacts.keys should beEmpty()
                    content.childRepositories should beEmpty()
                }
        }

        "return a failure result if there other errors" {
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/artifacts.jar"))
                    .willReturn(
                        aResponse().withStatus(500)
                    )
            )

            P2RepositoryContentLoader.loadRepositoryContent(tempdir(), server.repositoryUrl())
                .shouldBeFailure { exception ->
                    exception.shouldBeInstanceOf<HttpDownloadError>().code shouldBe 500
                }
        }

        "extract the URLs of child repositories" {
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/compositeArtifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("compositeArtifacts.xml")
                    )
            )

            P2RepositoryContentLoader.loadRepositoryContent(tempdir(), server.repositoryUrl())
                .shouldBeSuccess { content ->
                    content.baseUrl shouldBe server.repositoryUrl()
                    content.artifacts.keys should beEmpty()
                    content.childRepositories should containExactlyInAnyOrder(
                        "https://p2.example.org/test/repository",
                        server.repositoryUrl("/child/repository")
                    )
                }
        }

        "extract the URLs of child repositories from a compositeArtifacts.jar file" {
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/compositeArtifacts.jar"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("compositeArtifacts.jar")
                    )
            )

            P2RepositoryContentLoader.loadRepositoryContent(tempdir(), server.repositoryUrl())
                .shouldBeSuccess { content ->
                    content.baseUrl shouldBe server.repositoryUrl()
                    content.artifacts.keys should beEmpty()
                    content.childRepositories should containExactlyInAnyOrder(
                        "https://p2.example.org/test/repository",
                        server.repositoryUrl("/child/repository")
                    )
                }
        }

        "handle a base URL ending on a slash correctly" {
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/artifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("artifacts1.xml")
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/compositeArtifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("compositeArtifacts.xml")
                    )
            )

            P2RepositoryContentLoader.loadRepositoryContent(tempdir(), "${server.repositoryUrl()}/")
                .shouldBeSuccess { content ->
                    content.baseUrl shouldBe server.repositoryUrl()
                    content.artifacts.keys shouldHaveSize 2
                    content.childRepositories shouldHaveSize 2
                }
        }
    }

    "loadAllRepositoryContents()" should {
        "load the contents of multiple repositories" {
            val basePath2 = "/repo2/downloads"
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/artifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("artifacts1.xml")
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("$basePath2/artifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("artifacts2.xml")
                    )
            )

            val (contents, issues) = P2RepositoryContentLoader.loadAllRepositoryContents(
                listOf(server.repositoryUrl(), server.repositoryUrl(basePath2))
            )

            issues should beEmpty()

            contents.flatMap { it.artifacts.keys } should containExactlyInAnyOrder(
                P2Identifier("org.apache.commons.io:2.8.0.v20210415-0900"),
                P2Identifier("org.apache.commons.codec:1.14.0.v20200818-1422"),
                P2Identifier("org.apache.commons.logging.source:1.2.0.v20180409-1502"),
                P2Identifier(
                    bundleId = "org.eclipse.orbit.releng.recipes.feature.aggregation.source:1.0.0.v20211212-1642",
                    classifier = "org.eclipse.update.feature"
                )
            )
        }

        "load the content of child repositories" {
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/compositeArtifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("compositeArtifacts2.xml")
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("/child1/repository/artifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("artifacts1.xml")
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("/child2/repository/artifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("artifacts2.xml")
                    )
            )

            val (contents, _) = P2RepositoryContentLoader.loadAllRepositoryContents(
                listOf(server.repositoryUrl())
            )

            contents.flatMap { it.artifacts.keys } should containExactlyInAnyOrder(
                P2Identifier("org.apache.commons.io:2.8.0.v20210415-0900"),
                P2Identifier("org.apache.commons.codec:1.14.0.v20200818-1422"),
                P2Identifier("org.apache.commons.logging.source:1.2.0.v20180409-1502"),
                P2Identifier(
                    bundleId = "org.eclipse.orbit.releng.recipes.feature.aggregation.source:1.0.0.v20211212-1642",
                    classifier = "org.eclipse.update.feature"
                )
            )
        }

        "generate issues for failed downloads" {
            val basePath2 = "/repo2/downloads"
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/artifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(500)
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("$basePath2/artifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("artifacts2.xml")
                    )
            )

            val (contents, issues) = P2RepositoryContentLoader.loadAllRepositoryContents(
                listOf(server.repositoryUrl(), server.repositoryUrl(basePath2))
            )

            contents.flatMap { it.artifacts.keys } should containExactlyInAnyOrder(
                P2Identifier("org.apache.commons.logging.source:1.2.0.v20180409-1502"),
                P2Identifier(
                    bundleId = "org.eclipse.orbit.releng.recipes.feature.aggregation.source:1.0.0.v20211212-1642",
                    classifier = "org.eclipse.update.feature"
                )
            )

            issues.shouldBeSingleton { issue ->
                issue.severity shouldBe Severity.ERROR
                issue.source shouldBe "Tycho"
                issue.message shouldContain server.repositoryUrl()
            }
        }

        "generate a warning for repositories with no artifacts" {
            val basePath2 = "/repo2/empty"
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/artifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("artifacts2.xml")
                    )
            )

            val (contents, issues) = P2RepositoryContentLoader.loadAllRepositoryContents(
                listOf(server.repositoryUrl(), server.repositoryUrl(basePath2))
            )

            contents.flatMap { it.artifacts.keys } should containExactlyInAnyOrder(
                P2Identifier("org.apache.commons.logging.source:1.2.0.v20180409-1502"),
                P2Identifier(
                    bundleId = "org.eclipse.orbit.releng.recipes.feature.aggregation.source:1.0.0.v20211212-1642",
                    classifier = "org.eclipse.update.feature"
                )
            )

            issues.shouldBeSingleton { issue ->
                issue.severity shouldBe Severity.WARNING
                issue.source shouldBe "Tycho"
                issue.message shouldContain server.repositoryUrl(basePath2)
            }
        }

        "load each repository only once and handle cycles in repository child references" {
            server.stubFor(
                get(urlPathEqualTo("$REPOSITORY_BASE_PATH/compositeArtifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("compositeArtifacts2.xml")
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("/child1/repository/compositeArtifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("compositeArtifactsCyclic.xml")
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("/child2/repository/artifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("artifacts1.xml")
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("/child3/repository/artifacts.xml"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("artifacts2.xml")
                    )
            )

            val (contents, issues) = P2RepositoryContentLoader.loadAllRepositoryContents(
                listOf(server.repositoryUrl())
            )

            issues should beEmpty()

            contents.flatMap { it.artifacts.keys } should containExactlyInAnyOrder(
                P2Identifier("org.apache.commons.io:2.8.0.v20210415-0900"),
                P2Identifier("org.apache.commons.codec:1.14.0.v20200818-1422"),
                P2Identifier("org.apache.commons.logging.source:1.2.0.v20180409-1502"),
                P2Identifier(
                    bundleId = "org.eclipse.orbit.releng.recipes.feature.aggregation.source:1.0.0.v20211212-1642",
                    classifier = "org.eclipse.update.feature"
                )
            )
        }
    }
})

/** The root folder for files to be downloaded from the WireMock server. */
private const val TEST_FILES_ROOT = "src/test/assets"

/** The base path of the test repository. */
private const val REPOSITORY_BASE_PATH = "/repo/downloads"

/** The URL under which the test repository hosted by the mock server can be reached. */
private fun WireMockServer.repositoryUrl(basePath: String = REPOSITORY_BASE_PATH): String =
    "http://localhost:${port()}$basePath"
