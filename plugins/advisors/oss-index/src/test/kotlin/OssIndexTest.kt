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
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.net.URI

import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.utils.common.enumSetOf

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
            val ossIndex = OssIndex(
                OssIndexFactory.descriptor,
                OssIndexConfiguration("http://localhost:${server.port()}", null, null)
            )
            val packages = COMPONENTS_REQUEST_IDS.mapTo(mutableSetOf()) {
                Package.EMPTY.copy(id = it, purl = it.toPurl())
            }

            val result = ossIndex.retrievePackageFindings(packages).mapKeys { it.key.id }

            result.keys should containExactly(ID_JUNIT)
            result[ID_JUNIT] shouldNotBeNull {
                advisor shouldBe ossIndex.details
                vulnerabilities should containExactly(
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
                                severity = "MEDIUM",
                                score = 5.5f,
                                vector = "CVSS:3.0/AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:N"
                            ),
                            VulnerabilityReference(
                                url = URI("https://nvd.nist.gov/vuln/detail/CVE-2020-15250"),
                                scoringSystem = "CVSS:3.0",
                                severity = "MEDIUM",
                                score = 5.5f,
                                vector = "CVSS:3.0/AV:L/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:N"
                            )
                        )
                    )
                )
            }
        }

        "handle a failure response from the server" {
            server.stubFor(
                post(urlPathEqualTo(COMPONENTS_REQUEST_URL))
                    .willReturn(
                        aResponse().withStatus(500)
                    )
            )
            val ossIndex = OssIndex(
                OssIndexFactory.descriptor,
                OssIndexConfiguration("http://localhost:${server.port()}", null, null)
            )
            val packages = COMPONENTS_REQUEST_IDS.mapTo(mutableSetOf()) {
                Package.EMPTY.copy(id = it, purl = it.toPurl())
            }

            val result = ossIndex.retrievePackageFindings(packages).mapKeys { it.key.id }

            result.keys shouldBe COMPONENTS_REQUEST_IDS
            result.forAll { (_, advisorResult) ->
                advisorResult.advisor shouldBe ossIndex.details
                advisorResult.vulnerabilities should beEmpty()
                advisorResult.summary.issues shouldHaveSingleElement { it.severity == Severity.ERROR }
            }
        }

        "provide correct details" {
            val ossIndex = OssIndex(
                OssIndexFactory.descriptor,
                OssIndexConfiguration("http://localhost:${server.port()}", null, null)
            )

            ossIndex.details shouldBe AdvisorDetails(ADVISOR_NAME, enumSetOf(AdvisorCapability.VULNERABILITIES))
        }
    }
})

private const val ADVISOR_NAME = "OSSIndex"

private const val TEST_FILES_ROOT = "src/test/assets"

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
                    .withBodyFile(responseFile)
            )
    )
}
