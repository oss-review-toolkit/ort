/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners.fossid

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlin.IllegalArgumentException

import org.ossreviewtoolkit.model.config.ScannerConfiguration

class FossIdConfigTest : WordSpec({
    "create" should {
        "throw if no options for FossID are provided in the scanner configuration" {
            val scannerConfig = ScannerConfiguration()

            shouldThrow<IllegalArgumentException> { FossIdConfig.create(scannerConfig) }
        }

        "read all properties from the scanner configuration" {
            val options = mapOf(
                "serverUrl" to SERVER_URL,
                "apiKey" to API_KEY,
                "user" to USER,
                "packageNamespaceFilter" to NAMESPACE_FILTER,
                "packageAuthorsFilter" to AUTHOR_FILTER,
                "waitForResult" to "false",
                "deltaScans" to "true",
                "deltaScanLimit" to "42",
                "timeout" to "300",
                "addAuthenticationToUrl" to "false"
            )
            val scannerConfig = options.toScannerConfig()

            val fossIdConfig = FossIdConfig.create(scannerConfig)

            fossIdConfig shouldBe FossIdConfig(
                serverUrl = SERVER_URL,
                apiKey = API_KEY,
                user = USER,
                packageAuthorsFilter = AUTHOR_FILTER,
                packageNamespaceFilter = NAMESPACE_FILTER,
                waitForResult = false,
                deltaScans = true,
                deltaScanLimit = 42,
                addAuthenticationToUrl = false,
                timeout = 300,
                options = options
            )
        }

        "set default values for optional properties" {
            val options = mapOf(
                "serverUrl" to SERVER_URL,
                "apiKey" to API_KEY,
                "user" to USER
            )
            val scannerConfig = options.toScannerConfig()

            val fossIdConfig = FossIdConfig.create(scannerConfig)

            fossIdConfig shouldBe FossIdConfig(
                serverUrl = SERVER_URL,
                apiKey = API_KEY,
                user = USER,
                packageAuthorsFilter = "",
                packageNamespaceFilter = "",
                waitForResult = true,
                deltaScans = false,
                deltaScanLimit = Int.MAX_VALUE,
                addAuthenticationToUrl = false,
                timeout = 60,
                options = options
            )
        }

        "throw if the server URL is missing" {
            val scannerConfig = mapOf(
                "apiKey" to API_KEY,
                "user" to USER
            ).toScannerConfig()

            shouldThrow<IllegalArgumentException> { FossIdConfig.create(scannerConfig) }
        }

        "throw if the API key is missing" {
            val scannerConfig = mapOf(
                "serverUrl" to SERVER_URL,
                "user" to USER
            ).toScannerConfig()

            shouldThrow<IllegalArgumentException> { FossIdConfig.create(scannerConfig) }
        }

        "throw if the user name is missing" {
            val scannerConfig = mapOf(
                "serverUrl" to SERVER_URL,
                "apiKey" to API_KEY
            ).toScannerConfig()

            shouldThrow<IllegalArgumentException> { FossIdConfig.create(scannerConfig) }
        }

        "throw if the deltaScanLimit is invalid" {
            val scannerConfig = mapOf(
                "serverUrl" to SERVER_URL,
                "apiKey" to API_KEY,
                "user" to USER,
                "deltaScanLimit" to "0"
            ).toScannerConfig()

            shouldThrow<IllegalArgumentException> { FossIdConfig.create(scannerConfig) }
        }
    }

    "createNamingProvider" should {
        "create a naming provider with a correct project naming convention" {
            val scannerConfig = mapOf(
                "serverUrl" to SERVER_URL,
                "apiKey" to API_KEY,
                "user" to USER,
                "namingProjectPattern" to "#projectName_\$Org_\$Unit",
                "namingVariableOrg" to "TestOrganization",
                "namingVariableUnit" to "TestUnit"
            ).toScannerConfig()

            val fossIdConfig = FossIdConfig.create(scannerConfig)
            val namingProvider = fossIdConfig.createNamingProvider()

            val projectName = namingProvider.createProjectCode("TestProject")

            projectName shouldBe "TestProject_TestOrganization_TestUnit"
        }

        "create a naming provider with a correct scan naming convention" {
            val scannerConfig = mapOf(
                "serverUrl" to SERVER_URL,
                "apiKey" to API_KEY,
                "user" to USER,
                "namingScanPattern" to "#projectName_\$Org_\$Unit_#deltaTag",
                "namingVariableOrg" to "TestOrganization",
                "namingVariableUnit" to "TestUnit"
            ).toScannerConfig()

            val fossIdConfig = FossIdConfig.create(scannerConfig)
            val namingProvider = fossIdConfig.createNamingProvider()

            val scanCode = namingProvider.createScanCode("TestProject", FossId.DeltaTag.DELTA)

            scanCode shouldBe "TestProject_TestOrganization_TestUnit_delta"
        }
    }

    "createService" should {
        "create a correctly configured FossIdRestService" {
            val loginPage = "Welcome to FossID"
            val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

            server.start()

            try {
                val serverUrl = "http://localhost:${server.port()}"
                val scannerConfig = mapOf(
                    "serverUrl" to serverUrl,
                    "apiKey" to API_KEY,
                    "user" to USER
                ).toScannerConfig()

                server.stubFor(
                    get(urlPathEqualTo("/index.php"))
                        .withQueryParam("form", equalTo("login"))
                        .willReturn(
                            aResponse().withStatus(200)
                                .withBody(loginPage)
                        )
                )

                val fossIdConfig = FossIdConfig.create(scannerConfig)
                val service = fossIdConfig.createService()

                service.getLoginPage().string() shouldBe loginPage
            } finally {
                server.stop()
            }
        }
    }
})

private const val SERVER_URL = "https://www.example.org/fossid"
private const val API_KEY = "test_api_key"
private const val USER = "fossIdTestUser"
private const val NAMESPACE_FILTER = "testFilterForNamespace"
private const val AUTHOR_FILTER = "testFilterForAuthors"

/**
 * Return a [ScannerConfiguration] with this map as options for the FossID scanner.
 */
private fun Map<String, String>.toScannerConfig(): ScannerConfiguration {
    val options = mapOf("FossId" to this)
    return ScannerConfiguration(options = options)
}
