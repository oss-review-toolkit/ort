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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.time.Instant

import org.ossreviewtoolkit.clients.dos.JSON
import org.ossreviewtoolkit.clients.dos.PackageInfo
import org.ossreviewtoolkit.clients.dos.ScanResultsResponseBody
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.Secret
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.utils.test.readResource

class DosScannerTest : StringSpec({
    lateinit var scanner: DosScanner

    val server = WireMockServer(
        WireMockConfiguration.options().dynamicPort().notifier(ConsoleNotifier(false))
    )

    beforeTest {
        server.start()

        scanner = DosScannerFactory.create(
            url = "http://localhost:${server.port()}/api/",
            token = Secret(""),
            timeout = 60L,
            pollInterval = 5L,
            frontendUrl = "http://localhost:3000"
        )
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

        scanner.client.getScanResults(emptyList()) shouldBe null
    }

    "getScanResults() should return 'no-results' when no results in db" {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(readResource("/no-results.json"))
                )
        )

        val status = scanner.client.getScanResults(
            listOf(
                PackageInfo(
                    purl = "purl",
                    declaredLicenseExpressionSPDX = null
                )
            )
        )?.state?.status

        status shouldBe "no-results"
    }

    "getScanResults() should return 'pending' when scan ongoing" {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(readResource("/pending.json"))
                )
        )

        val response = scanner.client.getScanResults(
            listOf(
                PackageInfo(
                    purl = "purl",
                    declaredLicenseExpressionSPDX = null
                )
            )
        )

        response?.state?.status shouldBe "pending"
        response?.state?.jobId shouldBe "dj34eh4h65"
    }

    "getScanResults() should return 'ready' plus the results when results in db" {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(readResource("/ready.json"))
                )
        )

        val response = scanner.client.getScanResults(
            listOf(
                PackageInfo(
                    purl = "purl",
                    declaredLicenseExpressionSPDX = null
                )
            )
        )

        val actualJson = JSON.encodeToString(response?.results)
        val expectedJson = JSON.decodeFromString<ScanResultsResponseBody>(readResource("/ready.json")).let {
            JSON.encodeToString(it.results)
        }

        response?.state?.status shouldBe "ready"
        response?.state?.jobId shouldBe null
        actualJson shouldBe expectedJson
    }

    "runBackendScan() with failing upload URL retrieval should throw a ScanException" {
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

        shouldThrow<ScanException> {
            scanner.runBackendScan(
                packages = listOf(
                    PackageInfo(
                        purl = pkg.purl,
                        declaredLicenseExpressionSPDX = null
                    )
                ),
                sourceDir = tempdir(),
                startTime = Instant.now()
            )
        }
    }

    "scanPackage() should return existing results" {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(readResource("/ready.json"))
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

    "scanPackage() should throw a ScanException when fetching presigned URL fails" {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(readResource("/no-results.json"))
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

        shouldThrow<ScanException> {
            scanner.scanPackage(
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
        }
    }
})
