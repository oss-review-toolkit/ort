package org.ossreviewtoolkit.plugins.scanners.dos

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.matchers.shouldBe

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.apache.logging.log4j.kotlin.Logging

import org.junit.jupiter.api.*

import org.junit.jupiter.api.Assertions.*
import org.ossreviewtoolkit.clients.dos.DOSService

import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import java.time.Instant

class DOSTest {

    private lateinit var dos: DOS
    private companion object : Logging
    private val json = Json { prettyPrint = true }

    private val server = WireMockServer(WireMockConfiguration
        .options()
        .dynamicPort()
        .notifier(ConsoleNotifier(false))
    )

    private fun getResourceAsString(resourceName: String): String {
        return DOSTest::class.java.getResource(resourceName)?.readText(Charsets.UTF_8) ?: "xxx"
    }

    @BeforeEach
    fun setup() {
        server.start()
        val scannerOptions = mapOf(DOSConfig.SERVER_URL_PROPERTY to "http://localhost:${server.port()}/api/")
        val configuration = ScannerConfiguration(options = mapOf("DOS" to scannerOptions))
        dos = DOS.Factory().create(configuration, DownloaderConfiguration())
    }

    @AfterEach
    fun teardown() {
        server.stop()
    }

    @Test
    fun `getScanResults() should return null when service unavailable`() {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                )
        )
        runBlocking {
            dos.repository.getScanResults("purl") shouldBe null
        }
    }

    @Test
    fun `getScanResults() should return 'no-results' when no results in db`() {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(getResourceAsString("/no-results.json"))
                )
        )
        runBlocking {
            val status = dos.repository.getScanResults("purl")?.state?.status
            status shouldBe "no-results"
        }
    }

    @Test
    fun `getScanResults() should return 'pending' when scan ongoing`() {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(getResourceAsString("/pending.json"))
                )
        )
        runBlocking {
            val response = dos.repository.getScanResults("purl")
            val status = response?.state?.status
            val id = response?.state?.id
            status shouldBe "pending"
            id shouldBe "dj34eh4h65"
        }
    }

    @Test
    fun `getScanResults() should return 'ready' plus the results for results in db`() {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(getResourceAsString("/ready.json"))
                )
        )
        runBlocking {
            val response = dos.repository.getScanResults("pkg:npm/mime-types@2.1.18")
            val status = response?.state?.status
            val id = response?.state?.id

            val resultsJson = json.encodeToString(response?.results)
            val readyResponse = json.decodeFromString<DOSService.ScanResultsResponseBody>(getResourceAsString("/ready.json"))
            val expectedJson = json.encodeToString(readyResponse.results)

            status shouldBe "ready"
            id shouldBe null
            resultsJson shouldBe expectedJson
        }
    }

    @Test
    fun `runBackendScan() with failing presigned URL retrieval should abort and log an issue`() {
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
                pkg,
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

    @Test
    fun `scanPackage() should return existing results`() {
        server.stubFor(
            post(urlEqualTo("/api/scan-results"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(getResourceAsString("/ready.json"))
                )
        )
        val pkg = Package.EMPTY.copy(
            purl = "pkg:npm/mime-types@2.1.18",
            vcsProcessed = VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/jshttp/mime-types.git",
                revision = "076f7902e3a730970ea96cd0b9c09bb6110f1127",
                path = ""
            )
        )

        val scanResult = dos.scanPackage(pkg, ScanContext(
            labels = emptyMap(),
            packageType = PackageType.PROJECT,
            excludes = null
        ))
        scanResult.summary.licenseFindings.size shouldBe 4
        scanResult.summary.copyrightFindings.size shouldBe 2
        scanResult.summary.issues.size shouldBe 0
    }
}
