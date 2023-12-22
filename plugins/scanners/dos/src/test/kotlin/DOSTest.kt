package org.ossreviewtoolkit.plugins.scanners.dos

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.time.Instant

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.clients.dos.DOSService
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScannerWrapperConfig
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

class DOSTest : StringSpec({
    lateinit var dos: DOS
    val json = Json { prettyPrint = true }

    val server = WireMockServer(
        WireMockConfiguration.options().dynamicPort().notifier(ConsoleNotifier(false))
    )

    fun getResourceAsString(resourceName: String): String = checkNotNull(javaClass.getResource(resourceName)).readText()

    beforeTest {
        server.start()
        val config = DOSConfig(
            serverUrl = "http://localhost:${server.port()}/api/",
            serverToken = "",
            pollInterval = 5,
            restTimeout = 60,
            fetchConcluded = false,
            frontendUrl = "http://localhost:3000"
        )
        dos = DOS.Factory().create(config, ScannerWrapperConfig.EMPTY)
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
        runBlocking {
            dos.repository.getScanResults(emptyList(), false) shouldBe null
        }
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
        runBlocking {
            val status = dos.repository.getScanResults(listOf("purl"), false)?.state?.status
            status shouldBe "no-results"
        }
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
        runBlocking {
            val response = dos.repository.getScanResults(listOf("purl"), false)
            val status = response?.state?.status
            val jobId = response?.state?.jobId
            status shouldBe "pending"
            jobId shouldBe "dj34eh4h65"
        }
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
        runBlocking {
            val response = dos.repository.getScanResults(listOf("purl"), false)
            val status = response?.state?.status
            val jobId = response?.state?.jobId

            val resultsJson = json.encodeToString(response?.results)
            val readyResponse = json.decodeFromString<DOSService.ScanResultsResponseBody>(
                getResourceAsString("/ready.json")
            )
            val expectedJson = json.encodeToString(readyResponse.results)

            status shouldBe "ready"
            jobId shouldBe null
            resultsJson shouldBe expectedJson
        }
    }

    "runBackendScan() with failing presigned URL retrieval should abort and log an issue" {
        server.stubFor(
            post(urlEqualTo("/api/upload-url"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                )
        )
        val dosDir = createOrtTempDir()
        val tmpDir = "/tmp/"
        val thisScanStartTime = Instant.now()
        val issues = mutableListOf<Issue>()
        val pkg = Package.EMPTY.copy(
            id = Identifier("Maven:org.apache.commons:commons-lang3:3.9"),
            binaryArtifact = RemoteArtifact.EMPTY.copy(url = "https://www.apache.org/dist/commons/commons-lang3/3.9/")
        )
        val result = runBlocking {
            dos.runBackendScan(
                listOf(pkg.purl),
                dosDir,
                tmpDir,
                thisScanStartTime,
                issues
            )
        }
        if (result != null) {
            result.state.status shouldBe "failed"
            issues.size shouldBe 1
            issues[0].message shouldBe "Could not get a presigned URL for this package"
            issues[0].severity shouldBe Severity.ERROR
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

        val scanResult = dos.scanPackage(
            pkg,
            ScanContext(
                labels = emptyMap(),
                packageType = PackageType.PROJECT,
                coveredPackages = listOf(pkg)
            )
        )

        scanResult.summary.licenseFindings.size shouldBe 3
        scanResult.summary.copyrightFindings.size shouldBe 2
        scanResult.summary.issues.size shouldBe 0
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

        val scanResult = dos.scanPackage(
            pkg,
            ScanContext(
                labels = emptyMap(),
                packageType = PackageType.PROJECT,
                coveredPackages = listOf(pkg)
            )
        )

        scanResult.summary.licenseFindings.size shouldBe 0
        scanResult.summary.copyrightFindings.size shouldBe 0
        scanResult.summary.issues.size shouldBe 1
        scanResult.summary.issues[0].message shouldBe "Could not get a presigned URL for this package"
        scanResult.summary.issues[0].severity shouldBe Severity.ERROR
    }
})
