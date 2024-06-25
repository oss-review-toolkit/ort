/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.dos

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import java.time.Instant

import kotlinx.serialization.encodeToString

import org.ossreviewtoolkit.clients.dos.JSON
import org.ossreviewtoolkit.clients.dos.ScanResultsResponseBody
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScannerWrapperConfig
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance

class DosScannerTest : StringSpec({
    lateinit var scanner: DosScanner

    val server = WireMockServer(
        WireMockConfiguration.options().dynamicPort().notifier(ConsoleNotifier(false))
    )

    fun getResourceAsString(resourceName: String): String = checkNotNull(javaClass.getResource(resourceName)).readText()

    beforeTest {
        server.start()

        val config = DosScannerConfig(
            url = "http://localhost:${server.port()}/api/",
            token = "",
            timeout = 60L,
            pollInterval = 5L,
            fetchConcluded = false,
            frontendUrl = "http://localhost:3000"
        )

        scanner = DosScanner.Factory().create(config, ScannerWrapperConfig.EMPTY)
    }

    afterTest {
        server.stop()
    }

    "getScanResults() should return null when service unavailable" {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                )
        )

        scanner.client.getScanResults(emptyList(), false) shouldBe null
    }

    "getScanResults() should return 'no-results' when no results in db" {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(getResourceAsString("/no-results.json"))
                )
        )

        val status = scanner.client.getScanResults(listOf("purl"), false)?.state?.status

        status shouldBe "no-results"
    }

    "getScanResults() should return 'pending' when scan ongoing" {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(getResourceAsString("/pending.json"))
                )
        )

        val response = scanner.client.getScanResults(listOf("purl"), false)

        response?.state?.status shouldBe "pending"
        response?.state?.jobId shouldBe "dj34eh4h65"
    }

    "getScanResults() should return 'ready' plus the results when results in db" {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(getResourceAsString("/ready.json"))
                )
        )

        val response = scanner.client.getScanResults(listOf("purl"), false)

        val actualJson = JSON.encodeToString(response?.results)
        val expectedJson = JSON.decodeFromString<ScanResultsResponseBody>(getResourceAsString("/ready.json")).let {
            JSON.encodeToString(it.results)
        }

        response?.state?.status shouldBe "ready"
        response?.state?.jobId shouldBe null
        actualJson shouldBe expectedJson
    }

    "runBackendScan() with failing upload URL retrieval should abort and log an issue" {
        server.stubFor(
            post(urlEqualTo("/api/upload-url"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                )
        )

        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:org.apache.commons:commons-lang3:3.9"),
            binaryArtifact = RemoteArtifact.EMPTY.copy(url = "https://www.apache.org/dist/commons/commons-lang3/3.9/")
        )
        val issues = mutableListOf<Issue>()

        val result = scanner.runBackendScan(
            purls = listOf(pkg.purl),
            sourceDir = tempdir(),
            startTime = Instant.now(),
            issues = issues
        )

        result should beNull()
        issues shouldHaveSize 1

        with(issues.first()) {
            message shouldStartWith "Unable to get an upload URL for "
            severity shouldBe Severity.ERROR
        }
    }

    "scanPackage() should return existing results" {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(getResourceAsString("/ready.json"))
                )
        )

        val pkg = Package.EMPTY.copy(
            purl = "pkg:npm/mime-db@1.33.0",
            vcsProcessed = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/jshttp/mime-db.git",
                revision = "e7c849b1c70ff745a4ae456a0cd5e6be8b05c2fb",
                path = ""
            )
        )

        val scanResult = scanner.scanPackage(
            NestedProvenance(
                root = RepositoryProvenance(
                    vcsInfo = pkg.vcsProcessed,
                    resolvedRevision = pkg.vcsProcessed.revision
                ),
                subRepositories = emptyMap()
            ),
            ScanContext(
                labels = emptyMap(),
                packageType = PackageType.PROJECT,
                coveredPackages = listOf(pkg)
            )
        )

        with(scanResult.summary) {
            licenseFindings shouldHaveSize 3
            copyrightFindings shouldHaveSize 2
            issues should beEmpty()
        }
    }

    "scanPackage() should abort and log an issue when fetching presigned URL fails" {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(getResourceAsString("/no-results.json"))
                )
        )

        server.stubFor(
            post(urlEqualTo("/api/upload-url"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                )
        )

        val pkg = Package.EMPTY.copy(
            purl = "pkg:npm/mime-db@1.33.0",
            vcsProcessed = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/jshttp/mime-db.git",
                revision = "e7c849b1c70ff745a4ae456a0cd5e6be8b05c2fb",
                path = ""
            )
        )

        val scanResult = scanner.scanPackage(
            NestedProvenance(
                root = RepositoryProvenance(
                    vcsInfo = pkg.vcsProcessed,
                    resolvedRevision = pkg.vcsProcessed.revision
                ),
                subRepositories = emptyMap()
            ),
            ScanContext(
                labels = emptyMap(),
                packageType = PackageType.PROJECT,
                coveredPackages = listOf(pkg)
            )
        )

        with(scanResult.summary) {
            licenseFindings should beEmpty()
            copyrightFindings should beEmpty()

            issues shouldHaveSize 1

            with(issues.first()) {
                message shouldStartWith "Unable to get an upload URL for "
                severity shouldBe Severity.ERROR
            }
        }
    }
})
