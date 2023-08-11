package org.ossreviewtoolkit.plugins.scanners.dos

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.apache.logging.log4j.kotlin.Logging
import org.eclipse.jetty.util.ajax.JSON
import org.junit.jupiter.api.*

import org.junit.jupiter.api.Assertions.*
import org.ossreviewtoolkit.clients.dos.DOSService

import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration

class DOSTest {

    private lateinit var dos: DOS
    private companion object : Logging
    val json = Json { prettyPrint = true }

    val server = WireMockServer(WireMockConfiguration
        .options()
        .dynamicPort()
        .notifier(ConsoleNotifier(false))
    )

    fun getResourceAsString(resourceName: String): String {
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
            val response = dos.repository.getScanResults("purl")
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
}
