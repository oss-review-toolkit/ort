/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.storages

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.net.ServerSocket
import java.time.Duration
import java.time.Instant

import org.ossreviewtoolkit.clearlydefined.ComponentType
import org.ossreviewtoolkit.clearlydefined.Provider
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration

private const val PACKAGE_TYPE = "Maven"
private const val NAMESPACE = "someNamespace"
private const val NAME = "somePackage"
private const val VERSION = "0.1.8"
private const val COMMIT = "02b7f3d06fcbbedb44563aaa88ab62db3669946e"
private const val SCANCODE_VERSION = "3.2.2"

/** The name of the file with the test results from ClearlyDefined for the source artifact. */
private const val RESULT_FILE_SRC = "clearlydefined_scancode1.json"

/** The name of the file with the test results from ClearlyDefined for the source code repository. */
private const val RESULT_FILE_VCS = "clearlydefined_scancode2.json"

/** Path to a file contained in the test ClearlyDefined result for the source artifact. */
private const val TEST_PATH_SRC =
    "src/main/java/org/apache/commons/configuration2/tree/ConfigurationNodeVisitorAdapter.java"

/** Path to a file contained in the test ClearlyDefined result for the source code repository. */
private const val TEST_PATH_VCS =
    "src/main/java/org/apache/commons/configuration2/tree/ExpressionEngine.java"

/** A delta for comparing timestamps against the current time. */
private val MAX_TIME_DELTA = Duration.ofSeconds(30)

/** The ClearlyDefined URL referencing the test package. */
private const val PACKAGE_URL = "maven/mavencentral/$NAMESPACE/$NAME/$VERSION"

/** The ClearlyDefined URL referencing the source artifact of the test package. */
private val SRC_URL = "${ComponentType.SOURCE_ARCHIVE.value}/mavencentral/$NAMESPACE/$NAME/$VERSION"

/** The ClearlyDefined URL referencing the source code repository of the test package. */
private val VCS_URL = "${ComponentType.GIT.value}/${Provider.GITHUB.value}/$NAMESPACE/$NAME/$COMMIT"

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
private val SCANNER_DETAILS =
    ScannerDetails("scancode", SCANCODE_VERSION, "aConfig")

/**
 * Return a test package to be queried from the ClearlyDefined storage that can be configured to have a
 * [source artifact][withSourceArtifact] and/or [VCS information][withVcsInfo].
 */
private fun createTestPackage(withSourceArtifact: Boolean = false, withVcsInfo: Boolean = false): Package =
    if (!withSourceArtifact && !withVcsInfo) TEST_PACKAGE
    else {
        val srcArtifact = if (withSourceArtifact) {
            RemoteArtifact("https://source-artifact.org/test", Hash.NONE)
        } else {
            TEST_PACKAGE.sourceArtifact
        }

        val vcsInfo = if (withVcsInfo) {
            VcsInfo(
                VcsType.GIT, "https://github.com/$NAMESPACE/$NAME.git", revision = "tag1",
                resolvedRevision = COMMIT
            )
        } else {
            TEST_PACKAGE.vcs
        }

        TEST_PACKAGE.copy(vcs = vcsInfo, sourceArtifact = srcArtifact)
    }

/**
 * Return a storage configuration that points to the mock [server].
 */
private fun storageConfiguration(server: WireMockServer): ClearlyDefinedStorageConfiguration {
    val url = "http://localhost:${server.port()}"
    return ClearlyDefinedStorageConfiguration(url)
}

/**
 * Generate the URL used by ClearlyDefined to reference the results for a package with the given [prefix]
 * produced by the tool with the [toolName] and [toolVersion].
 */
private fun toolUrl(prefix: String, toolName: String, toolVersion: String): String =
    "$prefix/$toolName/$toolVersion"

/**
 * Stub a request for the available harvest tools on the [wiremock] server for the package with
 * given [packageUrl] to return the specified [tools].
 */
private fun stubHarvestTools(wiremock: WireMockServer, packageUrl: String, tools: List<String>) {
    val urlPath = "/harvest/$packageUrl"
    val response = tools.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
    wiremock.stubFor(
        get(urlPathEqualTo(urlPath))
            .withQueryParam("form", equalTo("list"))
            .willReturn(
                aResponse().withStatus(200)
                    .withBody(response)
            )
    )
}

/**
 * Stub a request for the harvested data from ScanCode for the given [packageUrl] on the [wiremock] server to
 * return the [resultFile] specified.
 */
private fun stubHarvestToolResponse(wiremock: WireMockServer, packageUrl: String, resultFile: String) {
    val urlPath = "/harvest/${toolUrl(packageUrl, "scancode", SCANCODE_VERSION)}"
    wiremock.stubFor(
        get(urlPathEqualTo(urlPath))
            .withQueryParam("form", equalTo("streamed"))
            .willReturn(
                aResponse().withStatus(200)
                    .withBodyFile(resultFile)
            )
    )
}

/**
 * Check that the given [result] contains expected data for the given [Identifier][expId]. For each of the
 * [paths] specified, the result container should contain a result that lists this path.
 */
private fun assertValidResult(
    result: Result<ScanResultContainer>,
    vararg paths: String,
    expId: Identifier = TEST_IDENTIFIER
): List<ScanResult> =
    when (result) {
        is Success -> {
            result.result.id shouldBe expId
            result.result.results shouldHaveSize paths.size
            paths.forEach { path ->
                result.result.results.find { scanResult ->
                    scanResult.summary.licenseFindings.find {
                        it.location.path == path && it.license.licenses().contains("Apache-2.0")
                    } != null
                }.shouldNotBeNull()
            }
            result.result.results
        }

        is Failure -> fail("Expected success result, but got Failure(${result.error})")
    }

/**
 * Check whether the given [time] is close to the current time. This is used to check whether correct
 * timestamps are set.
 */
private fun assertCurrentTime(time: Instant) {
    val delta = Duration.between(time, Instant.now())
    delta.isNegative shouldBe false
    delta.compareTo(MAX_TIME_DELTA) shouldBeLessThan 0
}

class ClearlyDefinedStorageTest : WordSpec({
    lateinit var wiremock: WireMockServer

    beforeSpec {
        wiremock = WireMockServer(
            WireMockConfiguration.options()
                .dynamicPort()
                .usingFilesUnderDirectory("src/test/assets/")
        )
        wiremock.start()
        WireMock.configureFor(wiremock.port())
    }

    afterSpec {
        wiremock.stop()
    }

    beforeTest {
        wiremock.resetAll()
    }

    "ClearlyDefinedStorage" should {
        "return an empty result for an unsupported scanner" {
            val pkg = createTestPackage(withVcsInfo = true)
            val details = SCANNER_DETAILS.copy(name = "scancode2")

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, details))
            wiremock.allServeEvents.shouldBeEmpty()
        }

        "load existing scan results for a package from ClearlyDefined from the source artifact" {
            val pkg = createTestPackage(withSourceArtifact = true)
            stubHarvestTools(
                wiremock, SRC_URL,
                listOf(toolUrl(SRC_URL, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(wiremock, SRC_URL, RESULT_FILE_SRC)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_DETAILS), TEST_PATH_SRC)
        }

        "load existing scan results for a package from ClearlyDefined from the source repository" {
            val pkg = createTestPackage(withVcsInfo = true)
            stubHarvestTools(
                wiremock, VCS_URL,
                listOf(toolUrl(VCS_URL, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(wiremock, VCS_URL, RESULT_FILE_VCS)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_DETAILS), TEST_PATH_VCS)
        }

        "load all scan results from ClearlyDefined available for a package" {
            val pkg = createTestPackage(withVcsInfo = true, withSourceArtifact = true)
            stubHarvestTools(
                wiremock, SRC_URL,
                listOf(toolUrl(SRC_URL, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(wiremock, SRC_URL, RESULT_FILE_SRC)
            stubHarvestTools(
                wiremock, VCS_URL,
                listOf(toolUrl(VCS_URL, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(wiremock, VCS_URL, RESULT_FILE_VCS)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_DETAILS), TEST_PATH_VCS, TEST_PATH_SRC)
        }

        "choose the correct tool URL if there are multiple" {
            val pkg = createTestPackage(withSourceArtifact = true)
            val tools = listOf(
                toolUrl(SRC_URL, "someOtherTool", "08-15"),
                "a-completely-different-tool",
                toolUrl(SRC_URL, "scancode", SCANCODE_VERSION)
            )
            stubHarvestTools(wiremock, SRC_URL, tools)
            stubHarvestToolResponse(wiremock, SRC_URL, RESULT_FILE_SRC)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_DETAILS), TEST_PATH_SRC)
        }

        "ignore results for a different ScanCode version" {
            val pkg = createTestPackage(withVcsInfo = true)
            stubHarvestTools(
                wiremock, VCS_URL,
                listOf(toolUrl(VCS_URL, "scancode", "3.1.9"))
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_DETAILS))
        }

        "detect a compatible ScanCode version" {
            val details = SCANNER_DETAILS.copy(name = "ScanCode", version = "3.2.3")
            val pkg = createTestPackage(withSourceArtifact = true)
            stubHarvestTools(
                wiremock, SRC_URL,
                listOf(toolUrl(SRC_URL, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(wiremock, SRC_URL, RESULT_FILE_SRC)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, details), TEST_PATH_SRC)
        }

        "load existing scan results for an identifier from ClearlyDefined" {
            stubHarvestTools(
                wiremock, PACKAGE_URL,
                listOf(toolUrl(PACKAGE_URL, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(wiremock, PACKAGE_URL, RESULT_FILE_SRC)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(TEST_IDENTIFIER), TEST_PATH_SRC)
        }

        "set correct metadata in the package scan result" {
            val pkg = createTestPackage(withVcsInfo = true)
            stubHarvestTools(
                wiremock, VCS_URL,
                listOf(toolUrl(VCS_URL, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(wiremock, VCS_URL, RESULT_FILE_VCS)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            val result = assertValidResult(storage.read(pkg, SCANNER_DETAILS), TEST_PATH_VCS)[0]
            result.scanner.name shouldBe "ScanCode"
            result.scanner.version shouldBe "3.2.2"
            assertCurrentTime(result.summary.startTime)
            assertCurrentTime(result.summary.endTime)
        }

        "handle failed responses from ClearlyDefined" {
            val pkg = createTestPackage(withVcsInfo = true, withSourceArtifact = true)
            stubFor(
                get(anyUrl())
                    .willReturn(aResponse().withStatus(500))
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            when (val result = storage.read(pkg, SCANNER_DETAILS)) {
                is Success -> fail("Expected failure, but got $result")
                is Failure -> {
                    result.error shouldContain SRC_URL
                    result.error shouldContain VCS_URL
                }
            }
        }

        "handle the case that no results for  the scancode tool are available" {
            val pkg = createTestPackage(withSourceArtifact = true)
            val tools = listOf(toolUrl(SRC_URL, "unknownTool", "unknownVersion"), "differentTool")
            stubHarvestTools(wiremock, SRC_URL, tools)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_DETAILS))
        }

        "handle the case that no result for the tool file is returned" {
            val pkg = createTestPackage(withVcsInfo = true)
            val scanCodeUrl = toolUrl(PACKAGE_URL, "scancode", SCANCODE_VERSION)
            stubHarvestTools(wiremock, VCS_URL, listOf(scanCodeUrl))
            stubFor(
                get(urlPathEqualTo("/harvest/$scanCodeUrl"))
                    .willReturn(aResponse().withStatus(200))
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_DETAILS))
        }

        "only use VCS info pointing to GitHub" {
            val vcs = VcsInfo(VcsType.GIT, "https://gitlab.com/foo/bar.git", VERSION, COMMIT)
            val pkg = TEST_PACKAGE.copy(vcs = vcs)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_DETAILS))
        }

        "deal with a package identifier not supported by ClearlyDefined" {
            val id = TEST_IDENTIFIER.copy(type = "unknown")
            val pkg = createTestPackage(withSourceArtifact = true).copy(id = id)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_DETAILS), expId = id)
        }

        "handle an unexpected result for the harvest tool request" {
            val pkg = createTestPackage(withVcsInfo = true)
            stubFor(
                get(anyUrl())
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBody("This is not a JSON response")
                    )
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            when (val result = storage.read(pkg, SCANNER_DETAILS)) {
                is Failure -> result.error shouldContain "JsonParseException"
                else -> fail("Unexpected result: $result")
            }
        }

        "handle an unexpected result for the harvest tool file request" {
            val pkg = createTestPackage(withSourceArtifact = true)
            val scanCodeUrl = toolUrl(SRC_URL, "scancode", SCANCODE_VERSION)
            stubHarvestTools(wiremock, SRC_URL, listOf(scanCodeUrl))
            stubFor(
                get(urlPathEqualTo("/harvest/$scanCodeUrl"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBody("{ \"unexpected\": true }")
                    )
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_DETAILS))
        }

        "handle a failure to connect to the server" {
            // find a port on which no service is running
            val port = ServerSocket(0).use { it.localPort }
            val serverUrl = "http://localhost:$port"

            val storage = ClearlyDefinedStorage(ClearlyDefinedStorageConfiguration((serverUrl)))

            when (val result = storage.read(TEST_IDENTIFIER)) {
                is Failure -> result.error shouldContain "ConnectException"
                else -> fail("Unexpected result: $result")
            }
        }

        "deal with identifiers not supported by ClearlyDefined" {
            val id = TEST_IDENTIFIER.copy(type = "unknown")

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(id), expId = id)
        }
    }
})
