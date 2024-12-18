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
import org.ossreviewtoolkit.plugins.api.Secret

class FossIdConfigTest : WordSpec({
    "create" should {
        "throw if the deltaScanLimit is invalid" {
            shouldThrow<IllegalArgumentException> {
                FossIdFactory.create(
                    serverUrl = SERVER_URL,
                    user = Secret(USER),
                    apiKey = Secret(API_KEY),
                    deltaScanLimit = 0
                )
            }
        }

        "throw if the sensitivity is invalid" {
            shouldThrow<IllegalArgumentException> {
                FossIdFactory.create(
                    serverUrl = SERVER_URL,
                    user = Secret(USER),
                    apiKey = Secret(API_KEY),
                    sensitivity = 21
                )
            }
        }
    }

    "createNamingProvider" should {
        "create a naming provider with a correct scan naming convention" {
            val fossId = FossIdFactory.create(
                serverUrl = SERVER_URL,
                user = Secret(USER),
                apiKey = Secret(API_KEY),
                namingScanPattern = "#repositoryName_#deltaTag"
            )

            val namingProvider = fossId.config.createNamingProvider()

            val scanCode = namingProvider.createScanCode("TestProject", FossId.DeltaTag.DELTA)

            scanCode shouldBe "TestProject_delta"
        }
    }

    "createUrlProvider" should {
        "initialize correct URL mappings" {
            val url = "https://changeit.example.org/foo"
            val fossId = FossIdFactory.create(
                serverUrl = SERVER_URL,
                user = Secret(USER),
                apiKey = Secret(API_KEY),
                urlMappings = "$url -> $SERVER_URL"
            )

            val urlProvider = fossId.config.createUrlProvider()

            urlProvider.getUrl(url) shouldBe SERVER_URL
        }
    }

    "createService" should {
        "create a correctly configured FossIdRestService" {
            val loginPage = "Welcome to FossID"
            val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

            server.start()

            try {
                server.stubFor(
                    get(urlPathEqualTo("/index.php"))
                        .withQueryParam("form", equalTo("login"))
                        .willReturn(
                            aResponse().withStatus(200)
                                .withBody(loginPage)
                        )
                )

                val service = FossIdRestService.create("http://localhost:${server.port()}")

                service.getLoginPage().string() shouldBe loginPage
            } finally {
                server.stop()
            }
        }
    }
})

private const val SERVER_URL = "https://www.example.org/fossid/"
