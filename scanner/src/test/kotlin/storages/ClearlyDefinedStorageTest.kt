/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
 * Copyright (C) 2021 HERE Europe B.V.
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

import com.vdurmont.semver4j.Semver

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import java.io.File
import java.net.ServerSocket
import java.time.Duration
import java.time.Instant

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
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.scanner.ScannerCriteria

private const val PACKAGE_TYPE = "Maven"
private const val NAMESPACE = "someNamespace"
private const val NAME = "somePackage"
private const val VERSION = "0.1.8"
private const val COMMIT = "02b7f3d06fcbbedb44563aaa88ab62db3669946e"
private const val SCANCODE_VERSION = "3.2.2"

private const val TEST_FILES_ROOT = "src/test/assets"
private const val TEST_FILES_DIRECTORY = "clearly-defined"

/** The name of the file with the test response from ClearlyDefined. */
private const val RESPONSE_FILE = "scancode-$SCANCODE_VERSION.json"

/** A delta for comparing timestamps against the current time. */
private val MAX_TIME_DELTA = Duration.ofSeconds(30)

/** The ClearlyDefined coordinates referencing the test package. */
private val COORDINATES = Coordinates(ComponentType.MAVEN, Provider.MAVEN_CENTRAL, NAMESPACE, NAME, VERSION)

/** Path to a file contained in the test ClearlyDefined result. */
private const val TEST_PATH =
    "src/main/java/org/apache/commons/configuration2/tree/DefaultExpressionEngine.java"

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
        declaredLicenses = sortedSetOf(),
        description = "test package description",
        homepageUrl = "https://www.test-package.com",
        vcs = VcsInfo.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        binaryArtifact = RemoteArtifact.EMPTY
    )

/** The scanner details used by tests. */
private val SCANNER_CRITERIA =
    ScannerCriteria(
        "aScanner", Semver("1.0.0"), Semver("2.0.0"),
        ScannerCriteria.exactConfigMatcher("aConfig")
    )

/** The template for a ClearlyDefined definitions request. */
private val DEFINITIONS_TEMPLATE = readDefinitionsTemplate()

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
            .withQueryParam("form", equalTo("streamed"))
            .willReturn(
                aResponse().withStatus(200)
                    .withBodyFile("$TEST_FILES_DIRECTORY/$RESPONSE_FILE")
            )
    )
}

/**
 * Stub a request for the definitions endpoint for the given [coordinates] on the [server] server.
 */
private fun stubDefinitions(server: WireMockServer, coordinates: Coordinates = COORDINATES) {
    val coordinatesList = listOf(coordinates)
    val expectedBody = jsonMapper.writeValueAsString(coordinatesList)
    server.stubFor(
        post(urlPathEqualTo("/definitions"))
            .withRequestBody(equalToJson(expectedBody))
            .willReturn(
                aResponse().withStatus(200)
                    .withBody(DEFINITIONS_TEMPLATE.replace(PACKAGE_VARIABLE, coordinates.toString()))
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
            finding.location.path == TEST_PATH && "Apache-2.0" in finding.license.licenses()
        } shouldNot beNull()

        if (block != null) scanResult.block()
    }
}

/**
 * Check whether this [Instant] is close to the current time. This is used to check whether correct timestamps are set.
 */
private fun Instant.shouldBeCloseToCurrentTime(maxDelta: Duration = MAX_TIME_DELTA) {
    val delta = Duration.between(this, Instant.now())
    delta.isNegative shouldBe false
    delta.compareTo(maxDelta) shouldBeLessThan 0
}

/**
 * Read the template for a ClearlyDefines definitions request from the test file.
 */
private fun readDefinitionsTemplate(): String {
    val templateFile = File("$TEST_FILES_ROOT/cd_definitions.json")
    return templateFile.readText()
}

class ClearlyDefinedStorageTest : WordSpec({
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

    "ClearlyDefinedStorage" should {
        "load existing scan results for a package from ClearlyDefined" {
            stubHarvestTools(
                server, COORDINATES,
                listOf(toolUrl(COORDINATES, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(server, COORDINATES)
            stubDefinitions(server)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(TEST_PACKAGE, SCANNER_CRITERIA).shouldBeValid()
        }

        "load existing scan results for an identifier from ClearlyDefined" {
            stubHarvestTools(
                server, COORDINATES,
                listOf(toolUrl(COORDINATES, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(server, COORDINATES)
            stubDefinitions(server)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(TEST_IDENTIFIER).shouldBeValid()
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

            storage.read(TEST_PACKAGE, SCANNER_CRITERIA).shouldBeValid()
        }

        "set correct metadata in the package scan result" {
            stubHarvestTools(
                server, COORDINATES,
                listOf(toolUrl(COORDINATES, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(server, COORDINATES)
            stubDefinitions(server)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(TEST_IDENTIFIER).shouldBeValid {
                scanner.name shouldBe "ScanCode"
                scanner.version shouldBe "3.0.2"
                summary.startTime.shouldBeCloseToCurrentTime()
                summary.endTime.shouldBeCloseToCurrentTime()
            }
        }

        "return a failure if a ClearlyDefined request fails" {
            server.stubFor(
                get(anyUrl())
                    .willReturn(aResponse().withStatus(500))
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            val result = storage.read(TEST_IDENTIFIER)

            result.shouldBeFailure {
                it.message shouldContain "HttpException"
            }
        }

        "return an empty result if no results for the scancode tool are available" {
            val tools = listOf(toolUrl(COORDINATES, "unknownTool", "unknownVersion"), "differentTool")
            stubHarvestTools(server, COORDINATES, tools)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(TEST_IDENTIFIER).shouldBeSuccess {
                it should beEmpty()
            }
        }

        "return an empty result if no result for the tool file is returned" {
            val scanCodeUrl = toolUrl(COORDINATES, "scancode", SCANCODE_VERSION)
            stubHarvestTools(server, COORDINATES, listOf(scanCodeUrl))
            server.stubFor(
                get(urlPathEqualTo("/harvest/$scanCodeUrl"))
                    .willReturn(aResponse().withStatus(200))
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(TEST_PACKAGE, SCANNER_CRITERIA).shouldBeSuccess {
                it should beEmpty()
            }
        }

        "use GitHub VCS info if available" {
            val gitUrl = Coordinates(ComponentType.GIT, Provider.GITHUB, NAMESPACE, NAME, COMMIT)
            val vcsGit = VcsInfo(
                VcsType.GIT,
                "https://github.com/$NAMESPACE/$NAME.git",
                COMMIT
            )
            val pkg = TEST_PACKAGE.copy(vcs = vcsGit)
            val tools = listOf(toolUrl(gitUrl, "scancode", SCANCODE_VERSION))
            stubHarvestTools(server, gitUrl, tools)
            stubHarvestToolResponse(server, gitUrl)
            stubDefinitions(server, gitUrl)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(pkg, SCANNER_CRITERIA).shouldBeValid()
        }

        "only use VCS info pointing to GitHub" {
            val vcs = VcsInfo(VcsType.GIT, "https://gitlab.com/foo/bar.git", COMMIT)
            val pkg = TEST_PACKAGE.copy(vcs = vcs)
            val tools = listOf(toolUrl(COORDINATES, "scancode", SCANCODE_VERSION))
            stubHarvestTools(server, COORDINATES, tools)
            stubHarvestToolResponse(server, COORDINATES)
            stubDefinitions(server)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(pkg, SCANNER_CRITERIA).shouldBeValid()
        }

        "use information from a source artifact if available" {
            val sourceArtifact = RemoteArtifact("https://source-artifact.org/test", Hash.NONE)
            val expUrl = COORDINATES.copy(type = ComponentType.SOURCE_ARCHIVE)
            val pkg = TEST_PACKAGE.copy(sourceArtifact = sourceArtifact)
            val tools = listOf(toolUrl(expUrl, "scancode", SCANCODE_VERSION))
            stubHarvestTools(server, expUrl, tools)
            stubHarvestToolResponse(server, expUrl)
            stubDefinitions(server, expUrl)

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(pkg, SCANNER_CRITERIA).shouldBeValid()
        }

        "return an empty result if the coordinates are not supported by ClearlyDefined" {
            val id = TEST_IDENTIFIER.copy(type = "unknown")

            val storage = ClearlyDefinedStorage(storageConfiguration(server))

            storage.read(id).shouldBeSuccess {
                it should beEmpty()
            }
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

            val result = storage.read(TEST_IDENTIFIER)

            result.shouldBeFailure {
                it.message shouldContain "JsonParseException"
            }
        }

        "return an empty result if a harvest tool file request returns an unexpected result" {
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

            storage.read(TEST_IDENTIFIER).shouldBeSuccess {
                it should beEmpty()
            }
        }

        "return a failure if the connection to the server fails" {
            // Find a port on which no service is running.
            val port = ServerSocket(0).use { it.localPort }
            val serverUrl = "http://localhost:$port"

            val storage = ClearlyDefinedStorage(ClearlyDefinedStorageConfiguration((serverUrl)))

            val result = storage.read(TEST_IDENTIFIER)

            result.shouldBeFailure {
                it.message shouldContain "Connection refused"
            }
        }
    }
})
