/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.storages

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import java.net.ServerSocket
import java.time.Duration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.clients.clearlydefined.ComponentType
import org.ossreviewtoolkit.clients.clearlydefined.Coordinates
import org.ossreviewtoolkit.clients.clearlydefined.Provider
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.scanner.ScanStorageException
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.test.readResource

class ClearlyDefinedStorageTest : WordSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
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

    "ClearlyDefinedStorage" should {
        "handle a SocketTimeoutException" {
            server.stubFor(
                get(anyUrl())
                    .willReturn(aResponse().withFixedDelay(100))
            )
            val client = OkHttpClientHelper.buildClient {
                readTimeout(Duration.ofMillis(1))
            }

            val storage = ClearlyDefinedStorage("http://localhost:${server.port()}", client)

            storage.read(TEST_PACKAGE).shouldBeFailure<ScanStorageException>()
        }

        "load existing scan results for a package from ClearlyDefined" {
            stubHarvestTools(
                server, COORDINATES,
                listOf(toolUrl(COORDINATES, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(server, COORDINATES)
            stubDefinitions(server)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(TEST_PACKAGE).shouldBeValid()
        }

        "choose the correct tool URL if there are multiple" {
            val tools = listOf(
                toolUrl(COORDINATES, "someOtherTool", "08-15"),
                "a-completely-different-tool",
                toolUrl(COORDINATES, "scancode", SCANCODE_VERSION)
            )
            stubHarvestTools(server, COORDINATES, tools)
            stubHarvestToolResponse(server, COORDINATES)
            stubDefinitions(server)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(TEST_PACKAGE).shouldBeValid()
        }

        "set correct metadata in the package scan result" {
            stubHarvestTools(
                server, COORDINATES,
                listOf(toolUrl(COORDINATES, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(server, COORDINATES)
            stubDefinitions(server)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(TEST_PACKAGE).shouldBeValid {
                scanner.name shouldBe "ScanCode"
                scanner.version shouldBe SCANCODE_VERSION
            }
        }

        "return a failure if a ClearlyDefined request fails" {
            server.stubFor(
                get(anyUrl())
                    .willReturn(aResponse().withStatus(500))
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            val result = storage.read(TEST_PACKAGE)

            result shouldBeFailure {
                it.message shouldContain "HttpException"
            }
        }

        "return an empty result if no results for the scancode tool are available" {
            val tools = listOf(toolUrl(COORDINATES, "unknownTool", "unknownVersion"), "differentTool")
            stubHarvestTools(server, COORDINATES, tools)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(TEST_PACKAGE) shouldBeSuccess {
                it should beEmpty()
            }
        }

        "return a failure if no result for the tool file is returned" {
            val scanCodeUrl = toolUrl(COORDINATES, "scancode", SCANCODE_VERSION)
            stubHarvestTools(server, COORDINATES, listOf(scanCodeUrl))
            server.stubFor(
                get(urlPathEqualTo("/harvest/$scanCodeUrl"))
                    .willReturn(aResponse().withStatus(200))
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(TEST_PACKAGE) shouldBeFailure {
                it.message shouldContain "HttpException"
            }
        }

        "use GitHub VCS info if available" {
            val gitUrl = Coordinates(ComponentType.GIT, Provider.GITHUB, NAMESPACE, NAME, COMMIT)
            val vcsGit = VcsInfo(
                VcsType.GIT,
                "https://github.com/$NAMESPACE/$NAME.git",
                COMMIT
            )
            val pkg = TEST_PACKAGE.copy(vcs = vcsGit, vcsProcessed = vcsGit)
            val tools = listOf(toolUrl(gitUrl, "scancode", SCANCODE_VERSION))
            stubHarvestTools(server, gitUrl, tools)
            stubHarvestToolResponse(server, gitUrl)
            stubDefinitions(server, gitUrl)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(pkg).shouldBeValid()
        }

        "use information from a source artifact if available" {
            val sourceArtifact = RemoteArtifact("https://source-artifact.org/test", Hash.NONE)
            val coordinates = COORDINATES.copy(type = ComponentType.SOURCE_ARCHIVE)
            val pkg = TEST_PACKAGE.copy(sourceArtifact = sourceArtifact)
            val tools = listOf(toolUrl(coordinates, "scancode", SCANCODE_VERSION))
            stubHarvestTools(server, coordinates, tools)
            stubHarvestToolResponse(server, coordinates)
            stubDefinitions(server, coordinates)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(pkg).shouldBeValid()
        }

        "return a failure if the coordinates are not supported by ClearlyDefined" {
            val id = TEST_IDENTIFIER.copy(type = "unknown")
            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            val result = storage.read(TEST_PACKAGE.copy(id = id))

            result.shouldBeFailure<ScanStorageException>()
        }

        "return a failure if a harvest tool request returns an unexpected result" {
            server.stubFor(
                get(anyUrl())
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBody("This is not a JSON response")
                    )
            )
            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            val result = storage.read(TEST_PACKAGE)

            result.shouldBeFailure<ScanStorageException>()
        }

        "return a failure if a harvest tool file request returns an unexpected result" {
            val scanCodeUrl = toolUrl(COORDINATES, "scancode", SCANCODE_VERSION)
            stubHarvestTools(server, COORDINATES, listOf(scanCodeUrl))
            server.stubFor(
                get(urlPathEqualTo("/harvest/$scanCodeUrl"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBody("{ \"unexpected\": true }")
                    )
            )
            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            val result = storage.read(TEST_PACKAGE)

            result.shouldBeFailure<ScanStorageException>()
        }

        "return a failure if the connection to the server fails" {
            // Find a port on which no service is running.
            val port = withContext(Dispatchers.IO) { ServerSocket(0).use { it.localPort } }
            val serverUrl = "http://localhost:$port"

            val storage = ClearlyDefinedStorage(ClearlyDefinedStorageConfiguration((serverUrl)))

            val result = storage.read(TEST_PACKAGE)

            result shouldBeFailure {
                it.message shouldContain "Connection refused"
            }
        }
    }
})

private const val PACKAGE_TYPE = "Maven"
private const val NAMESPACE = "someNamespace"
private const val NAME = "somePackage"
private const val VERSION = "0.1.8"
private const val COMMIT = "02b7f3d06fcbbedb44563aaa88ab62db3669946e"
private const val SCANCODE_VERSION = "30.1.0"

/** The ClearlyDefined coordinates referencing the test package. */
private val COORDINATES = Coordinates(ComponentType.MAVEN, Provider.MAVEN_CENTRAL, NAMESPACE, NAME, VERSION)

/** Path to a file contained in the test ClearlyDefined result. */
private const val TEST_PATH = "META-INF/maven/com.vdurmont/semver4j/pom.xml"

private val TEST_IDENTIFIER =
    Identifier(
        type = PACKAGE_TYPE,
        namespace = NAMESPACE,
        name = NAME,
        version = VERSION
    )

private val TEST_PACKAGE =
    Package(
        id = TEST_IDENTIFIER,
        declaredLicenses = emptySet(),
        description = "test package description",
        homepageUrl = "https://www.test-package.com",
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        vcs = VcsInfo.EMPTY
    )

/** The template variable with the coordinates of the package that is requested. */
private const val PACKAGE_VARIABLE = "<<package>>"

/**
 * Return a storage configuration that points to the mock [server].
 */
private fun storageConfiguration(server: WireMockServer): ClearlyDefinedStorageConfiguration {
    val url = "http://localhost:${server.port()}"
    return ClearlyDefinedStorageConfiguration(url)
}

/**
 * Generate the URL used by ClearlyDefined to reference the results for a package with the given [coordinates]
 * produced by the tool with the [toolName] and [toolVersion].
 */
private fun toolUrl(coordinates: Coordinates, toolName: String, toolVersion: String): String =
    "$coordinates/$toolName/$toolVersion"

/**
 * Stub a request for the available harvest tools on the [server] server for the package with the given [coordinates]
 * to return the specified [tools].
 */
private fun stubHarvestTools(server: WireMockServer, coordinates: Coordinates, tools: List<String>) {
    val urlPath = "/harvest/$coordinates"
    val response = tools.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
    server.stubFor(
        get(urlPathEqualTo(urlPath))
            .withQueryParam("form", equalTo("list"))
            .willReturn(
                aResponse().withStatus(200)
                    .withBody(response)
            )
    )
}

/**
 * Stub a request for the harvested data from ScanCode for the given [coordinates] on the [server] server.
 */
private fun stubHarvestToolResponse(server: WireMockServer, coordinates: Coordinates) {
    val urlPath = "/harvest/${toolUrl(coordinates, "scancode", SCANCODE_VERSION)}"
    server.stubFor(
        get(urlPathEqualTo(urlPath))
            .withQueryParam("form", equalTo("raw"))
            .willReturn(
                aResponse().withStatus(200)
                    .withBodyFile("clearly-defined/scancode-$SCANCODE_VERSION.json")
            )
    )
}

/**
 * Stub a request for the definition's endpoint for the given [coordinates] on the [server] server.
 */
private fun TestConfiguration.stubDefinitions(server: WireMockServer, coordinates: Coordinates = COORDINATES) {
    val coordinatesList = listOf(coordinates)
    val expectedBody = ClearlyDefinedService.JSON.encodeToString(coordinatesList)
    val actualBody = readResource("/cd_definitions.json").replace(PACKAGE_VARIABLE, coordinates.toString())
    server.stubFor(
        post(urlPathEqualTo("/definitions"))
            .withRequestBody(equalToJson(expectedBody))
            .willReturn(
                aResponse().withStatus(200)
                    .withBody(actualBody)
            )
    )
}

/**
 * Check that this [Result] contains the expected data and return the first scan result from the list on success.
 */
private fun Result<List<ScanResult>>.shouldBeValid(block: (ScanResult.() -> Unit)? = null) {
    shouldBeSuccess {
        it shouldHaveSize 1

        val scanResult = it.first()
        scanResult.summary.licenseFindings.find { finding ->
            finding.location.path == TEST_PATH && "MIT" in finding.license.licenses()
        } shouldNot beNull()

        if (block != null) scanResult.block()
    }
}
