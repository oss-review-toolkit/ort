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

package org.ossreviewtoolkit.plugins.advisors.ossindex

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import java.net.URI

import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.plugins.api.Secret
import org.ossreviewtoolkit.utils.test.identifierToPackage

class OssIndexTest : WordSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
    )

    fun createOssIndex() =
        OssIndex(
            config = OssIndexConfiguration(
                serverUrl = "http://localhost:${server.port()}",
                username = "username",
                token = Secret("token")
            )
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

    "retrievePackageFindings()" should {
        "return vulnerability information" {
            server.stubComponentsRequest("response_components.json")
            val ossIndex = createOssIndex()

            val result = ossIndex.retrievePackageFindings(PACKAGES).mapKeys { it.key.id }

            result.keys should containExactly(PKG_JUNIT.id)
            result[PKG_JUNIT.id] shouldNotBeNull {
                advisor shouldBe ossIndex.details
                vulnerabilities.shouldBeSingleton {
                    it.id shouldBe "CVE-2020-15250"
                    it.summary shouldBe "Information Exposure"
                    it.description shouldStartWith "In JUnit4 from version 4.7 and before 4.13.1, the test rule "
                    it.references should containExactlyInAnyOrder(
                        VulnerabilityReference(
                            url = URI(
                                "https://guide.sonatype.com/vulnerability/CVE-2020-15250?component-type=maven" +
                                    "&component-name=junit%2Fjunit&utm_source=intellij&utm_medium=integration" +
                                    "&utm_content=HTTP"
                            ),
                            scoringSystem = "CVSS:3.1",
                            severity = "MEDIUM",
                            score = 5.5f,
                            vector = "CVSS:3.1/AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:N"
                        ),
                        VulnerabilityReference(
                            url = URI("http://web.nvd.nist.gov/view/vuln/detail?vulnId=CVE-2020-15250"),
                            scoringSystem = "CVSS:3.1",
                            severity = "MEDIUM",
                            score = 5.5f,
                            vector = "CVSS:3.1/AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:N"
                        ),
                        VulnerabilityReference(
                            url = URI("https://github.com/advisories/GHSA-269g-pwp5-87pp"),
                            scoringSystem = "CVSS:3.1",
                            severity = "MEDIUM",
                            score = 5.5f,
                            vector = "CVSS:3.1/AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:N"
                        )
                    )
                }
            }
        }

        "handle a failure response from the server" {
            server.stubFor(
                post(urlPathEqualTo(COMPONENTS_REQUEST_URL))
                    .willReturn(
                        aResponse().withStatus(500)
                    )
            )
            val ossIndex = createOssIndex()

            val result = ossIndex.retrievePackageFindings(PACKAGES).mapKeys { it.key.id.toCoordinates() }

            result.keys shouldContainExactlyInAnyOrder PACKAGES.map { it.id.toCoordinates() }
            result.keys.forAll { coordinates ->
                with(result.getValue(coordinates)) {
                    advisor shouldBe ossIndex.details
                    vulnerabilities should beEmpty()
                    summary.issues shouldHaveSingleElement { it.severity == Severity.ERROR }
                }
            }
        }
    }

    "details" should {
        "be correct" {
            val ossIndex = createOssIndex()

            ossIndex.details shouldBe AdvisorDetails(ADVISOR_NAME)
        }
    }
})

private const val ADVISOR_NAME = "OSSIndex"

private val PKG_HAMCREST = identifierToPackage("Maven:org.hamcrest:hamcrest-core:1.3")
private val PKG_JUNIT = identifierToPackage("Maven:junit:junit:4.12")
private const val COMPONENTS_REQUEST_URL = "/api/v3/authorized/component-report"
private val PACKAGES = setOf(PKG_HAMCREST, PKG_JUNIT)

private val COMPONENTS_REQUEST_JSON =
    PACKAGES.joinToString(prefix = "{ \"coordinates\": [", postfix = "] }") {
        "\"${it.purl}\""
    }

private fun WireMockServer.stubComponentsRequest(responseFile: String) {
    stubFor(
        post(urlPathEqualTo(COMPONENTS_REQUEST_URL))
            .withRequestBody(equalToJson(COMPONENTS_REQUEST_JSON, false, false))
            .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
            .willReturn(
                aResponse().withStatus(200)
                    .withBodyFile(responseFile)
            )
    )
}
