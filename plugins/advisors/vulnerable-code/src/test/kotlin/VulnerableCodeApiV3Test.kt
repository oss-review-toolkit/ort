/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.beEmpty as beEmptyMap
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

import java.net.URI

import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.plugins.advisors.api.normalizeVulnerabilityData

class VulnerableCodeApiV3Test : WordSpec({
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

    "retrievePackageFindings()" should {
        "return vulnerability information" {
            server.stubPackagesRequest("packages_response_packages.json")
            server.stubAdvisoriesRequest(
                "advisories_response_packages.json",
                generateAdvisoriesRequest(listOf(idLang, idStruts).map { it.toPurl() })
            )
            val api = createApi(server)
            val packagesToAdvise = inputPackagesFromAnalyzerResult()

            val result = api.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result.values.flatMap { it.summary.issues } should beEmpty()
            result shouldNot beEmptyMap()
            result.keys should containExactlyInAnyOrder(idLang, idStruts)

            val langResult = result.getValue(idLang)
            langResult.advisor shouldBe details

            val expLangVulnerability = Vulnerability(
                id = "CVE-2014-8242",
                references = listOf(
                    VulnerabilityReference(
                        URI("https://nvd.nist.gov/vuln/detail/CVE-2014-8242"),
                        scoringSystem = "cvssv3.1_qr",
                        severity = "LOW",
                        score = null,
                        vector = null
                    ),
                    VulnerabilityReference(
                        URI("https://nvd.nist.gov/vuln/detail/CVE-2014-8242"),
                        scoringSystem = null,
                        severity = null,
                        score = null,
                        vector = null
                    ),
                    VulnerabilityReference(
                        URI("https://github.com/advisories/GHSA-2cxf-6567-7pp6"),
                        scoringSystem = null,
                        severity = null,
                        score = null,
                        vector = null
                    )
                )
            )
            langResult.vulnerabilities.normalizeVulnerabilityData() should containExactly(expLangVulnerability)

            val strutsResult = result.getValue(idStruts)
            strutsResult.advisor shouldBe details

            val expStrutsVulnerabilities = listOf(
                Vulnerability(
                    id = "CVE-2009-1382",
                    references = listOf(
                        VulnerabilityReference(
                            URI("https://nvd.nist.gov/vuln/detail/CVE-2009-1382"),
                            scoringSystem = "cvssv2",
                            severity = "HIGH",
                            score = 7.0f,
                            vector = null
                        )
                    )
                ),
                Vulnerability(
                    id = "CVE-2019-CoV19",
                    references = listOf(
                        VulnerabilityReference(
                            URI("https://nvd.nist.gov/vuln/detail/CVE-2019-CoV19"),
                            scoringSystem = "cvssv3",
                            severity = "CRITICAL",
                            score = 10.0f,
                            vector = null
                        ),
                        VulnerabilityReference(
                            URI("https://nvd.nist.gov/vuln/detail/CVE-2019-CoV19"),
                            scoringSystem = "cvssv3.1_qr",
                            severity = "HIGH",
                            score = null,
                            vector = null
                        )
                    )
                )
            )
            strutsResult.vulnerabilities.normalizeVulnerabilityData() should
                containExactlyInAnyOrder(expStrutsVulnerabilities)
        }

        "extract the CVE ID from an alias" {
            server.stubPackagesRequest("packages_response_junit.json", request = generateV3PackagesRequest(idJUnit))
            server.stubAdvisoriesRequest("advisories_response_junit.json", generateAdvisoriesRequest(idJUnit))
            val api = createApi(server)
            val packagesToAdvise = inputPackagesFromIdentifiers(idJUnit)

            val result = api.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            val expJunitVulnerability = Vulnerability(
                id = "CVE-2020-15250",
                references = listOf(
                    VulnerabilityReference(
                        URI("http://people.canonical.com/~ubuntu-security/cve/2020/CVE-2020-15250.html"),
                        scoringSystem = "generic_textual",
                        severity = "Medium",
                        score = null,
                        vector = null
                    ),
                    VulnerabilityReference(
                        URI("http://people.canonical.com/~ubuntu-security/cve/2020/CVE-2020-15250.html"),
                        scoringSystem = "cvssv3",
                        severity = "MEDIUM",
                        score = 4.0f,
                        vector = "CVSS:3.1/AV:L/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N"
                    ),
                    VulnerabilityReference(
                        URI("https://access.redhat.com/hydra/rest/securitydata/cve/CVE-2020-15250.json"),
                        scoringSystem = "generic_textual",
                        severity = "Medium",
                        score = null,
                        vector = null
                    ),
                    VulnerabilityReference(
                        URI("https://access.redhat.com/hydra/rest/securitydata/cve/CVE-2020-15250.json"),
                        scoringSystem = "cvssv3",
                        severity = "MEDIUM",
                        score = 4.0f,
                        vector = "CVSS:3.1/AV:L/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N"
                    )
                )
            )
            result.getValue(idJUnit).vulnerabilities.normalizeVulnerabilityData() should
                containExactly(expJunitVulnerability)
        }

        "extract other official identifiers from the advisory ID" {
            server.stubPackagesRequest("packages_response_log4j.json", generateV3PackagesRequest(idLog4j))
            server.stubAdvisoriesRequest(
                "advisories_response_log4j_no_aliases.json",
                generateAdvisoriesRequest(idLog4j)
            )
            val api = createApi(server)
            val packagesToAdvise = inputPackagesFromIdentifiers(idLog4j)

            val result = api.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            val expLog4jVulnerabilities = listOf(
                Vulnerability(
                    id = "GHSA-8489-44mv-ggj8",
                    references = listOf(
                        VulnerabilityReference(
                            URI("https://github.com/advisories/GHSA-8489-44mv-ggj8.json"),
                            scoringSystem = null,
                            severity = null,
                            score = null,
                            vector = null
                        )
                    )
                )
            )
            result.getValue(idLog4j).vulnerabilities.normalizeVulnerabilityData() should
                containExactlyInAnyOrder(expLog4jVulnerabilities)
        }

        "prefer CVE ID from the advisory ID over aliases" {
            server.stubPackagesRequest("packages_response_log4j_cve.json", generateV3PackagesRequest(idLog4j))
            server.stubAdvisoriesRequest("advisories_response_log4j_cve.json", generateAdvisoriesRequest(idLog4j))
            val api = createApi(server)
            val packagesToAdvise = inputPackagesFromIdentifiers(idLog4j)

            val result = api.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            val expLog4jVulnerabilities = listOf(
                Vulnerability(
                    id = "CVE-2021-44832",
                    references = listOf(
                        VulnerabilityReference(
                            URI("https://github.com/advisories/GHSA-8489-44mv-ggj8.json"),
                            scoringSystem = "cvssv3",
                            severity = "MEDIUM",
                            score = 6.6f,
                            vector = "CVSS:3.1/AV:N/AC:H/PR:H/UI:N/S:U/C:H/I:H/A:H"
                        )
                    )
                )
            )
            result.getValue(idLog4j).vulnerabilities.normalizeVulnerabilityData() should
                containExactlyInAnyOrder(expLog4jVulnerabilities)
        }

        "gather purls from paginated response" {
            server.stubPackagesRequest("packages_response_page1.json")
            server.stubFor(
                post(urlPathEqualTo("/api/v3/packages/"))
                    .withQueryParam("page", equalTo("2"))
                    .withRequestBody(equalToJson(packagesRequestJson, true, false))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBodyFile("packages_response_page2.json")
                    )
            )
            server.stubAdvisoriesRequest(
                "advisories_response_packages_paginated.json",
                generateAdvisoriesRequest(listOf(idJUnit, idLang, idStruts).map { it.toPurl() })
            )
            val api = createApi(server)
            val packagesToAdvise = inputPackagesFromAnalyzerResult()

            val result = api.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result.values.flatMap { it.summary.issues } should beEmpty()
            result shouldNot beEmptyMap()
            result.keys should containExactlyInAnyOrder(idJUnit, idLang, idStruts)
        }

        "gather vulnerabilities from paginated advisories" {
            server.stubPackagesRequest(
                "packages_response_log4j_page_advisories.json",
                generateV3PackagesRequest(idLog4j)
            )
            server.stubAdvisoriesRequest("advisories_response_page1.json", generateAdvisoriesRequest(idLog4j))
            server.stubFor(
                post(urlPathEqualTo("/api/v3/advisories/"))
                    .withQueryParam("page", equalTo("2"))
                    .withRequestBody(equalToJson(generateAdvisoriesRequest(idLog4j), true, false))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBodyFile("advisories_response_page2.json")
                    )
            )
            val api = createApi(server)
            val packagesToAdvise = inputPackagesFromIdentifiers(idLog4j)

            val result = api.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result.keys shouldBe setOf(idLog4j)
            result.getValue(idLog4j).vulnerabilities shouldHaveSize 3

            with(result.getValue(idLog4j)) {
                vulnerabilities shouldHaveSize 3
                vulnerabilities.map {
                    it.id
                } shouldContainExactlyInAnyOrder setOf("CVE-2026-34480", "CVE-2026-34477", "CVE-2021-44832")
            }
        }

        "handle a failure response from the server" {
            server.stubFor(
                post(urlPathEqualTo("/api/v3/packages/"))
                    .willReturn(
                        aResponse().withStatus(500)
                    )
            )
            val api = createApi(server)
            val packagesToAdvise = inputPackagesFromAnalyzerResult()

            val result = api.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result shouldNotBeNull {
                keys should containExactly(packageIdentifiers)

                packageIdentifiers.forEach { pkg ->
                    with(getValue(pkg)) {
                        advisor shouldBe details
                        vulnerabilities should beEmpty()
                        summary.issues.shouldBeSingleton { issue ->
                            issue.severity shouldBe Severity.ERROR
                            issue.message shouldStartWith "ServerResponseException"
                            issue.message shouldContain "500 Server Error"
                        }
                    }
                }
            }
        }

        "filter out packages without vulnerabilities" {
            server.stubPackagesRequest("packages_response_packages_no_vulnerabilities.json")
            server.stubAdvisoriesRequest(
                "advisories_response_packages.json",
                generateAdvisoriesRequest(idStruts)
            )
            val api = createApi(server)
            val packagesToAdvise = inputPackagesFromAnalyzerResult()

            val result = api.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result.keys should containExactly(idStruts)
        }

        "handle unexpected packages in the query result" {
            server.stubPackagesRequest("packages_response_unexpected_packages.json")
            server.stubAdvisoriesRequest(
                "advisories_response_unexpected_packages.json",
                generateAdvisoriesRequest(
                    listOf(idLang.toPurl(), idStruts.toPurl(), "pkg:maven/org.unknown/unexpected@4.2")
                )
            )
            val api = createApi(server)
            val packagesToAdvise = inputPackagesFromAnalyzerResult()

            val result = api.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result.keys should containExactlyInAnyOrder(idLang, idStruts)
        }
    }
})

/**
 * The JSON request to query the test packages found in the result via the VulnerableCode API v3 packages endpoint.
 */
private val packagesRequestJson = generateV3PackagesRequest()

/**
 * Prepare this server to expect an API v3 packages [request] and to answer it with the given [responseFile].
 */
private fun WireMockServer.stubPackagesRequest(responseFile: String, request: String = packagesRequestJson) {
    stubFor(
        post(urlPathEqualTo("/api/v3/packages/"))
            .withRequestBody(
                equalToJson(request, /* ignoreArrayOrder = */ true, /* ignoreExtraElements = */ false)
            )
            .withHeader("Content-Type", equalTo("application/json"))
            .willReturn(
                aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile(responseFile)
            )
    )
}

/**
 * Prepare this server to expect an API v3 advisories [request] and to answer it with the given [responseFile].
 */
private fun WireMockServer.stubAdvisoriesRequest(responseFile: String, request: String) {
    stubFor(
        post(urlPathEqualTo("/api/v3/advisories/"))
            .withRequestBody(
                equalToJson(request, /* ignoreArrayOrder = */ true, /* ignoreExtraElements = */ false)
            )
            .withHeader("Content-Type", equalTo("application/json"))
            .willReturn(
                aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile(responseFile)
            )
    )
}

/**
 * Create a test instance of [VulnerableCode] that communicates with the local [server].
 */
private fun createApi(server: WireMockServer, apiUrl: Boolean = false): VulnerableCodeApiV3 =
    VulnerableCodeApiV3(
        VulnerableCodeFactory.descriptor,
        details,
        createConfig(server, VulnerableCodeApiVersion.V3, apiUrl)
    )
