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

import com.vdurmont.semver4j.Semver

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.net.ServerSocket
import java.time.Duration
import java.time.Instant

import org.ossreviewtoolkit.clients.clearlydefined.ComponentType
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.scanner.ScannerCriteria

private const val PACKAGE_TYPE = "Maven"
private const val NAMESPACE = "someNamespace"
private const val NAME = "somePackage"
private const val VERSION = "0.1.8"
private const val COMMIT = "02b7f3d06fcbbedb44563aaa88ab62db3669946e"
private const val SCANCODE_VERSION = "3.2.2"

/** The name of the file with the test results from ClearlyDefined. */
private const val RESULT_FILE = "clearlydefined_scancode.json"

/** A delta for comparing timestamps against the current time. */
private val MAX_TIME_DELTA = Duration.ofSeconds(30)

/** The ClearlyDefined URL referencing the test package. */
private const val PACKAGE_URL = "maven/mavencentral/$NAMESPACE/$NAME/$VERSION"

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
 * Stub a request for the harvested data from ScanCode for the given [packageUrl] on the [wiremock] server.
 */
private fun stubHarvestToolResponse(wiremock: WireMockServer, packageUrl: String) {
    val urlPath = "/harvest/${toolUrl(packageUrl, "scancode", SCANCODE_VERSION)}"
    wiremock.stubFor(
        get(urlPathEqualTo(urlPath))
            .withQueryParam("form", equalTo("streamed"))
            .willReturn(
                aResponse().withStatus(200)
                    .withBodyFile(RESULT_FILE)
            )
    )
}

/**
 * Check that the given [result] contains expected data.
 */
private fun assertValidResult(result: Result<ScanResultContainer>): ScanResult =
    when (result) {
        is Success -> {
            result.result.id shouldBe TEST_IDENTIFIER
            result.result.results shouldHaveSize 1
            val scanResult = result.result.results[0]
            scanResult.summary.licenseFindings.find {
                it.location.path == TEST_PATH && it.license.licenses().contains("Apache-2.0")
            }.shouldNotBeNull()
            scanResult
        }

        is Failure -> fail("Expected success result, but got Failure(${result.error})")
    }

/**
 * Check that the given [result] for package [expId] does not contain any data.
 */
private fun assertEmptyResult(result: Result<ScanResultContainer>, expId: Identifier = TEST_IDENTIFIER) {
    when (result) {
        is Success -> {
            result.result.id shouldBe expId
            result.result.results should beEmpty()
        }

        is Failure -> fail("Unexpected result: $result")
    }
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
    val wiremock = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory("src/test/assets/")
    )

    beforeSpec {
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
        "load existing scan results for a package from ClearlyDefined" {
            stubHarvestTools(
                wiremock, PACKAGE_URL,
                listOf(toolUrl(PACKAGE_URL, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(wiremock, PACKAGE_URL)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(TEST_PACKAGE, SCANNER_CRITERIA))
        }

        "load existing scan results for an identifier from ClearlyDefined" {
            stubHarvestTools(
                wiremock, PACKAGE_URL,
                listOf(toolUrl(PACKAGE_URL, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(wiremock, PACKAGE_URL)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(TEST_IDENTIFIER))
        }

        "choose the correct tool URL if there are multiple" {
            val tools = listOf(
                toolUrl(PACKAGE_URL, "someOtherTool", "08-15"),
                "a-completely-different-tool",
                toolUrl(PACKAGE_URL, "scancode", SCANCODE_VERSION)
            )
            stubHarvestTools(wiremock, PACKAGE_URL, tools)
            stubHarvestToolResponse(wiremock, PACKAGE_URL)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(TEST_PACKAGE, SCANNER_CRITERIA))
        }

        "set correct metadata in the package scan result" {
            stubHarvestTools(
                wiremock, PACKAGE_URL,
                listOf(toolUrl(PACKAGE_URL, "scancode", SCANCODE_VERSION))
            )
            stubHarvestToolResponse(wiremock, PACKAGE_URL)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            val result = assertValidResult(storage.read(TEST_IDENTIFIER))
            result.scanner.name shouldBe "ScanCode"
            result.scanner.version shouldBe "3.0.2"
            assertCurrentTime(result.summary.startTime)
            assertCurrentTime(result.summary.endTime)
        }

        "handle failed responses from ClearlyDefined" {
            stubFor(
                get(anyUrl())
                    .willReturn(aResponse().withStatus(500))
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            when (val result = storage.read(TEST_IDENTIFIER)) {
                is Success -> fail("Expected failure, but got $result")
                is Failure -> result.error shouldContain "IOException"
            }
        }

        "handle the case that no results for  the scancode tool are available" {
            val tools = listOf(toolUrl(PACKAGE_URL, "unknownTool", "unknownVersion"), "differentTool")
            stubHarvestTools(wiremock, PACKAGE_URL, tools)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertEmptyResult(storage.read(TEST_IDENTIFIER))
        }

        "handle the case that no result for the tool file is returned" {
            val scanCodeUrl = toolUrl(PACKAGE_URL, "scancode", SCANCODE_VERSION)
            stubHarvestTools(wiremock, PACKAGE_URL, listOf(scanCodeUrl))
            stubFor(
                get(urlPathEqualTo("/harvest/$scanCodeUrl"))
                    .willReturn(aResponse().withStatus(200))
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertEmptyResult(storage.read(TEST_PACKAGE, SCANNER_CRITERIA))
        }

        "use GitHub VCS info if available" {
            val gitUrl = "git/github/$NAMESPACE/$NAME/$COMMIT"
            val vcsGit = VcsInfo(
                VcsType.GIT,
                "https://github.com/$NAMESPACE/$NAME.git",
                VERSION,
                COMMIT
            )
            val pkg = TEST_PACKAGE.copy(vcs = vcsGit)
            val tools = listOf(toolUrl(gitUrl, "scancode", SCANCODE_VERSION))
            stubHarvestTools(wiremock, gitUrl, tools)
            stubHarvestToolResponse(wiremock, gitUrl)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_CRITERIA))
        }

        "only use VCS info pointing to GitHub" {
            val vcs = VcsInfo(VcsType.GIT, "https://gitlab.com/foo/bar.git", VERSION, COMMIT)
            val pkg = TEST_PACKAGE.copy(vcs = vcs)
            val tools = listOf(toolUrl(PACKAGE_URL, "scancode", SCANCODE_VERSION))
            stubHarvestTools(wiremock, PACKAGE_URL, tools)
            stubHarvestToolResponse(wiremock, PACKAGE_URL)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_CRITERIA))
        }

        "use information from a source artifact if available" {
            val sourceArtifact = RemoteArtifact("https://source-artifact.org/test", Hash.NONE)
            val expUrl = PACKAGE_URL.replace("maven/", ComponentType.SOURCE_ARCHIVE.value + "/")
            val pkg = TEST_PACKAGE.copy(sourceArtifact = sourceArtifact)
            val tools = listOf(toolUrl(expUrl, "scancode", SCANCODE_VERSION))
            stubHarvestTools(wiremock, expUrl, tools)
            stubHarvestToolResponse(wiremock, expUrl)

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertValidResult(storage.read(pkg, SCANNER_CRITERIA))
        }

        "deal with package coordinates not supported by ClearlyDefined" {
            val id = TEST_IDENTIFIER.copy(type = "unknown")

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertEmptyResult(storage.read(id), expId = id)
        }

        "handle an unexpected result for the harvest tool request" {
            stubFor(
                get(anyUrl())
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBody("This is not a JSON response")
                    )
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            when (val result = storage.read(TEST_IDENTIFIER)) {
                is Failure -> result.error shouldContain "JsonParseException"
                else -> fail("Unexpected result: $result")
            }
        }

        "handle an unexpected result for the harvest tool file request" {
            val scanCodeUrl = toolUrl(PACKAGE_URL, "scancode", SCANCODE_VERSION)
            stubHarvestTools(wiremock, PACKAGE_URL, listOf(scanCodeUrl))
            stubFor(
                get(urlPathEqualTo("/harvest/$scanCodeUrl"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBody("{ \"unexpected\": true }")
                    )
            )

            val storage = ClearlyDefinedStorage(storageConfiguration(wiremock))

            assertEmptyResult(storage.read(TEST_IDENTIFIER))
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
    }
})
