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

package org.ossreviewtoolkit.advisor.advisors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.beEmpty as beEmptyMap
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import java.net.URI

import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class OssIndexTest : WordSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory(TEST_FILES_ROOT)
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

    "OssIndex" should {
        "return vulnerability information" {
            server.stubComponentsRequest("response_components.json")
            val ossIndex = OssIndex(ADVISOR_NAME, "http://localhost:${server.port()}")
            val packages = COMPONENTS_REQUEST_IDS.map { Package.EMPTY.copy(id = it, purl = it.toPurl()) }

            val result = ossIndex.retrievePackageFindings(packages).mapKeys { it.key.id }

            result shouldNot beEmptyMap()
            result.keys should containExactlyInAnyOrder(ID_JUNIT)
            result[ID_JUNIT] shouldNotBeNull {
                this should haveSize(1)
                with(single()) {
                    advisor shouldBe ossIndex.details
                    vulnerabilities should containExactlyInAnyOrder(
                        Vulnerability(
                            id = "CVE-2020-15250",
                            summary = "[CVE-2020-15250] In JUnit4 from version 4.7 and before 4.13.1,...",
                            description = "In JUnit4 from version 4.7 and before 4.13.1, the test...",
                            references = listOf(
                                VulnerabilityReference(
                                    url = URI(
                                        "https://ossindex.sonatype.org/vulnerability/" +
                                            "7ea56ad4-8a8b-4e51-8ed9-5aad83d8efb1?component-type=maven" +
                                            "&component-name=junit.junit&utm_source=mozilla&utm_medium=integration" +
                                            "&utm_content=5.0"
                                    ),
                                    scoringSystem = "CVSS:3.0",
                                    severity = "5.5"
                                ),
                                VulnerabilityReference(
                                    url = URI("https://nvd.nist.gov/vuln/detail/CVE-2020-15250"),
                                    scoringSystem = "CVSS:3.0",
                                    severity = "5.5"
                                )
                            )
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
            val ossIndex = OssIndex(ADVISOR_NAME, "http://localhost:${server.port()}")
            val packages = COMPONENTS_REQUEST_IDS.map { Package.EMPTY.copy(id = it, purl = it.toPurl()) }

            val result = ossIndex.retrievePackageFindings(packages).mapKeys { it.key.id }

            result shouldNotBeNull {
                keys should containExactly(COMPONENTS_REQUEST_IDS)

                COMPONENTS_REQUEST_IDS.forEach { pkg ->
                    val pkgResults = getValue(pkg)
                    pkgResults shouldHaveSize 1
                    val pkgResult = pkgResults[0]
                    pkgResult.advisor shouldBe ossIndex.details
                    pkgResult.vulnerabilities should beEmpty()
                    pkgResult.summary.issues shouldHaveSize 1
                    val issue = pkgResult.summary.issues[0]
                    issue.severity shouldBe Severity.ERROR
                }
            }
        }

        "provide correct details" {
            val ossIndex = OssIndex(ADVISOR_NAME, "http://localhost:${server.port()}")

            ossIndex.details shouldBe AdvisorDetails(ADVISOR_NAME, enumSetOf(AdvisorCapability.VULNERABILITIES))
        }
    }
})

private const val ADVISOR_NAME = "OssIndexTest"

private const val TEST_FILES_ROOT = "src/test/assets"
private const val TEST_FILES_DIRECTORY = "oss-index"

private val ID_HAMCREST = Identifier("Maven:org.hamcrest:hamcrest-core:1.3")
private val ID_JUNIT = Identifier("Maven:junit:junit:4.12")

private const val COMPONENTS_REQUEST_URL = "/api/v3/component-report"
private val COMPONENTS_REQUEST_IDS = setOf(ID_HAMCREST, ID_JUNIT)

private val COMPONENTS_REQUEST_JSON =
    COMPONENTS_REQUEST_IDS.joinToString(prefix = "{ \"coordinates\": [", postfix = "] }") { "\"${it.toPurl()}\"" }

private fun WireMockServer.stubComponentsRequest(responseFile: String) {
    stubFor(
        post(urlPathEqualTo(COMPONENTS_REQUEST_URL))
            .withRequestBody(equalToJson(COMPONENTS_REQUEST_JSON, false, false))
            .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
            .willReturn(
                aResponse().withStatus(200)
                    .withBodyFile("$TEST_FILES_DIRECTORY/$responseFile")
            )
    )
}
