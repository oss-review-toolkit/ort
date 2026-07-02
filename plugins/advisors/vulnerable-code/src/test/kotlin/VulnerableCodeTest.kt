/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.vulnerablecode

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.AdvisorDetails

class VulnerableCodeTest : WordSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
    )

    beforeSpec {
        server.start()
    }

    afterSpec {
        server.stop()
    }

    beforeEach {
        server.resetAll()
    }

    "VulnerableCode" should {
        "provide correct AdvisorDetails" {
            val vulnerableCode = VulnerableCodeFactory.create()

            vulnerableCode.details shouldBe AdvisorDetails(ADVISOR_NAME)
        }

        "use the V1 API by default" {
            server.stubFor(
                post(urlPathEqualTo("/api/packages/bulk_search"))
                    .withRequestBody(
                        equalToJson(
                            generatePackagesRequest(idJUnit),
                            true,
                            false
                        )
                    )
                    .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withBodyFile("response_junit.json")
                    )
            )
            val vulnerableCode = VulnerableCodeFactory.create(serverUrl = "http://localhost:${server.port()}")
            val packagesToAdvise = inputPackagesFromIdentifiers(idJUnit)

            val result = vulnerableCode.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result.keys should containExactly(idJUnit)
            with(result.getValue(idJUnit)) {
                summary.issues shouldBe emptyList()
                vulnerabilities.map { it.id } should containExactly("CVE-2020-15250")
            }
        }
    }
})
