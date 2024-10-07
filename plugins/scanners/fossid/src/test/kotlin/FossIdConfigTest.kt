/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

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

import org.ossreviewtoolkit.clients.fossid.FossIdRestService

class FossIdConfigTest : WordSpec({
    "create" should {
        "throw if no options for FossID are provided in the scanner configuration" {
            shouldThrow<IllegalArgumentException> { FossIdConfig.create(emptyMap(), emptyMap()) }
        }

        "read all properties from the scanner configuration" {
            val options = mapOf(
                "serverUrl" to SERVER_URL,
                "waitForResult" to "false",
                "keepFailedScans" to "true",
                "deltaScans" to "true",
                "deltaScanLimit" to "42",
                "detectLicenseDeclarations" to "true",
                "detectCopyrightStatements" to "true",
                "timeout" to "300",
                "fetchSnippetMatchedLines" to "true",
                "snippetsLimit" to "1000"
            )

            val secrets = mapOf(
                "user" to USER,
                "apiKey" to API_KEY
            )

            val fossIdConfig = FossIdConfig.create(options, secrets)

            fossIdConfig shouldBe FossIdConfig(
                serverUrl = SERVER_URL,
                user = USER,
                apiKey = API_KEY,
                waitForResult = false,
                keepFailedScans = true,
                deltaScans = true,
                deltaScanLimit = 42,
                detectLicenseDeclarations = true,
                detectCopyrightStatements = true,
                timeout = 300,
                fetchSnippetMatchedLines = true,
                options = options,
                snippetsLimit = 1000,
                sensitivity = 10
            )
        }

        "set default values for optional properties" {
            val options = mapOf("serverUrl" to SERVER_URL)

            val secrets = mapOf(
                "user" to USER,
                "apiKey" to API_KEY
            )

            val fossIdConfig = FossIdConfig.create(options, secrets)

            fossIdConfig shouldBe FossIdConfig(
                serverUrl = SERVER_URL,
                user = USER,
                apiKey = API_KEY,
                waitForResult = true,
                keepFailedScans = false,
                deltaScans = false,
                deltaScanLimit = Int.MAX_VALUE,
                detectLicenseDeclarations = false,
                detectCopyrightStatements = false,
                timeout = 60,
                fetchSnippetMatchedLines = false,
                options = options,
                snippetsLimit = 500,
                sensitivity = 10
            )
        }

        "throw if the server URL is missing" {
            val secrets = mapOf(
                "user" to USER,
                "apiKey" to API_KEY
            )

            shouldThrow<IllegalArgumentException> { FossIdConfig.create(emptyMap(), secrets) }
        }

        "throw if the API key is missing" {
            val options = mapOf("serverUrl" to SERVER_URL)
            val secrets = mapOf("user" to USER)

            shouldThrow<IllegalArgumentException> { FossIdConfig.create(options, secrets) }
        }

        "throw if the user name is missing" {
            val options = mapOf("serverUrl" to SERVER_URL)
            val secrets = mapOf("apiKey" to API_KEY)

            shouldThrow<IllegalArgumentException> { FossIdConfig.create(options, secrets) }
        }

        "throw if the deltaScanLimit is invalid" {
            val options = mapOf(
                "serverUrl" to SERVER_URL,
                "deltaScanLimit" to "0"
            )

            val secrets = mapOf(
                "user" to USER,
                "apiKey" to API_KEY
            )

            shouldThrow<IllegalArgumentException> { FossIdConfig.create(options, secrets) }
        }

        "throw if the sensitivity is invalid" {
            val options = mapOf(
                "serverUrl" to SERVER_URL,
                "sensitivity" to "21"
            )

            val secrets = mapOf(
                "user" to USER,
                "apiKey" to API_KEY
            )

            shouldThrow<IllegalArgumentException> { FossIdConfig.create(options, secrets) }
        }
    }

    "createNamingProvider" should {
        "create a naming provider with a correct project naming convention" {
            val options = mapOf(
                "serverUrl" to SERVER_URL,
                "namingProjectPattern" to "#projectName_\$Org_\$Unit",
                "namingVariableOrg" to "TestOrganization",
                "namingVariableUnit" to "TestUnit"
            )

            val secrets = mapOf(
                "user" to USER,
                "apiKey" to API_KEY
            )

            val fossIdConfig = FossIdConfig.create(options, secrets)
            val namingProvider = fossIdConfig.createNamingProvider()

            val projectName = namingProvider.createProjectCode("TestProject")

            projectName shouldBe "TestProject_TestOrganization_TestUnit"
        }

        "create a naming provider with a correct scan naming convention" {
            val options = mapOf(
                "serverUrl" to SERVER_URL,
                "namingScanPattern" to "#projectName_\$Org_\$Unit_#deltaTag",
                "namingVariableOrg" to "TestOrganization",
                "namingVariableUnit" to "TestUnit"
            )

            val secrets = mapOf(
                "user" to USER,
                "apiKey" to API_KEY
            )

            val fossIdConfig = FossIdConfig.create(options, secrets)
            val namingProvider = fossIdConfig.createNamingProvider()

            val scanCode = namingProvider.createScanCode("TestProject", FossId.DeltaTag.DELTA)

            scanCode shouldBe "TestProject_TestOrganization_TestUnit_delta"
        }
    }

    "createUrlProvider" should {
        "initialize correct URL mappings" {
            val url = "https://changeit.example.org/foo"
            val options = mapOf(
                "serverUrl" to SERVER_URL,
                "urlMappingChangeHost" to "$url -> $SERVER_URL"
            )

            val secrets = mapOf(
                "user" to USER,
                "apiKey" to API_KEY
            )

            val fossIdConfig = FossIdConfig.create(options, secrets)
            val urlProvider = fossIdConfig.createUrlProvider()

            urlProvider.getUrl(url) shouldBe SERVER_URL
        }
    }

    "createService" should {
        "create a correctly configured FossIdRestService" {
            val loginPage = "Welcome to FossID"
            val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

            server.start()

            try {
                val serverUrl = "http://localhost:${server.port()}"
                val options = mapOf("serverUrl" to serverUrl)

                val secrets = mapOf(
                    "user" to USER,
                    "apiKey" to API_KEY
                )

                server.stubFor(
                    get(urlPathEqualTo("/index.php"))
                        .withQueryParam("form", equalTo("login"))
                        .willReturn(
                            aResponse().withStatus(200)
                                .withBody(loginPage)
                        )
                )

                val fossIdConfig = FossIdConfig.create(options, secrets)
                val service = FossIdRestService.create(fossIdConfig.serverUrl)

                service.getLoginPage().string() shouldBe loginPage
            } finally {
                server.stop()
            }
        }
    }
})

private const val SERVER_URL = "https://www.example.org/fossid"
