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

import java.io.File
import java.time.Instant

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.logging.log4j.kotlin.Logging
import org.apache.logging.log4j.Level
import org.junit.jupiter.api.*

import org.junit.jupiter.api.Assertions.*
import org.junit.platform.commons.logging.LoggerFactory

import org.ossreviewtoolkit.clients.dos.DOSRepository
import org.ossreviewtoolkit.clients.dos.DOSService
import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.common.withoutSuffix

class DOSTest {

    private lateinit var dos: DOS
    private companion object : Logging

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
                        .withBody(
                            """
                            {
                                "state": {
                                    "status": "no-results",
                                    "id": null
                                },
                                "results": []
                            }
                            """.trimIndent()
                        )
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
                        .withBody(
                            """
                            {
                                "state": {
                                    "status": "pending",
                                    "id": "dj34eh4h65"
                                },
                                "results": []
                            }
                            """.trimIndent()
                        )
                )
        )
        runBlocking {
            val status = dos.repository.getScanResults("purl")?.state?.status
            val id = dos.repository.getScanResults("purl")?.state?.id
            status shouldBe "pending"
            id shouldBe "dj34eh4h65"
        }
    }
}
