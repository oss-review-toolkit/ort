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

package org.ossreviewtoolkit.plugins.advisors.vulnerablecode

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.net.URI

import org.ossreviewtoolkit.advisor.normalizeVulnerabilityData
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.test.readResourceValue

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
        "return vulnerability information" {
            server.stubPackagesRequest("response_packages.json")
            val vulnerableCode = createVulnerableCode(server)
            val packagesToAdvise = inputPackagesFromAnalyzerResult()

            val result = vulnerableCode.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result.shouldNotBeEmpty()
            result.keys should containExactlyInAnyOrder(idLang, idStruts)

            val langResult = result.getValue(idLang)
            langResult.advisor shouldBe vulnerableCode.details

            val expLangVulnerability = Vulnerability(
                id = "CVE-2014-8242",
                references = listOf(
                    VulnerabilityReference(
                        URI("https://nvd.nist.gov/vuln/detail/CVE-2014-8242"),
                        scoringSystem = null,
                        severity = null,
                        score = null,
                        vector = null
                    ),
                    VulnerabilityReference(
                        URI("https://github.com/apache/commons-lang/security/advisories/GHSA-2cxf-6567-7pp6"),
                        scoringSystem = "cvssv3.1_qr",
                        severity = "LOW",
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
            strutsResult.advisor shouldBe vulnerableCode.details

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
            server.stubPackagesRequest("response_junit.json", request = generatePackagesRequest(idJUnit))
            val vulnerableCode = createVulnerableCode(server)
            val packagesToAdvise = inputPackagesFromIdentifiers(idJUnit)

            val result = vulnerableCode.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

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

        "extract other official identifiers from aliases" {
            server.stubPackagesRequest("response_log4j.json", generatePackagesRequest(idLog4j))
            val vulnerableCode = createVulnerableCode(server)
            val packagesToAdvise = inputPackagesFromIdentifiers(idLog4j)

            val result = vulnerableCode.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            val expLog4jVulnerabilities = listOf(
                Vulnerability(
                    id = "GHSA-jfh8-c2jp-5v3q",
                    references = listOf(
                        VulnerabilityReference(
                            URI("http://ref.com/files/165225/Apache-Log4j2-2.14.1-Remote-Code-Execution.html"),
                            scoringSystem = null,
                            severity = null,
                            score = null,
                            vector = null
                        )
                    )
                ),
                Vulnerability(
                    id = "CVE-2021-44832",
                    references = listOf(
                        VulnerabilityReference(
                            URI("https://access.redhat.com/hydra/rest/securitydata/cve/CVE-2021-44832.json"),
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

        "handle a failure response from the server" {
            server.stubFor(
                post(urlPathEqualTo("/packages/bulk_search"))
                    .willReturn(
                        aResponse().withStatus(500)
                    )
            )
            val vulnerableCode = createVulnerableCode(server)
            val packagesToAdvise = inputPackagesFromAnalyzerResult()

            val result = vulnerableCode.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result shouldNotBeNull {
                keys should containExactly(packageIdentifiers)

                packageIdentifiers.forEach { pkg ->
                    with(getValue(pkg)) {
                        advisor shouldBe vulnerableCode.details
                        vulnerabilities should beEmpty()
                        summary.issues.shouldBeSingleton { issue ->
                            issue.severity shouldBe Severity.ERROR
                            issue.message shouldBe "HttpException: HTTP 500 Server Error"
                        }
                    }
                }
            }
        }

        "filter out packages without vulnerabilities" {
            server.stubPackagesRequest("response_packages_no_vulnerabilities.json")
            val vulnerableCode = createVulnerableCode(server)
            val packagesToAdvise = inputPackagesFromAnalyzerResult()

            val result = vulnerableCode.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result.keys should containExactly(idStruts)
        }

        "handle unexpected packages in the query result" {
            server.stubPackagesRequest("response_unexpected_packages.json")
            val vulnerableCode = createVulnerableCode(server)
            val packagesToAdvise = inputPackagesFromAnalyzerResult()

            val result = vulnerableCode.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result.keys should containExactlyInAnyOrder(idLang, idStruts)
        }

        "provide correct AdvisorDetails" {
            val vulnerableCode = createVulnerableCode(server)

            vulnerableCode.details shouldBe AdvisorDetails(
                ADVISOR_NAME,
                enumSetOf(AdvisorCapability.VULNERABILITIES)
            )
        }
    }

    @Suppress("MaxLineLength")
    "fixupUrlEscaping()" should {
        "fixup a wrongly escaped ampersand" {
            val u = """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:oracle:retail_category_management_planning_\\&_optimization:16.0.3:*:*:*:*:*:*:*"""

            URI(u.fixupUrlEscaping()) shouldBe URI(
                """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:oracle:retail_category_management_planning_%26_optimization:16.0.3:*:*:*:*:*:*:*"""
            )
        }

        "fixup a wrongly escaped slash" {
            val u = """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:apple:swiftnio_http\/2:*:*:*:*:*:swift:*:*"""

            URI(u.fixupUrlEscaping()) shouldBe URI(
                """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:apple:swiftnio_http/2:*:*:*:*:*:swift:*:*"""
            )
        }

        "fixup a wrongly escaped plus" {
            val u = """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:oracle:hyperion_bi\+:*:*:*:*:*:*:*:*"""

            URI(u.fixupUrlEscaping()) shouldBe URI(
                """https://nvd.nist.gov/vuln/search/results?adv_search=true&isCpeNameSearch=true&query=cpe:2.3:a:oracle:hyperion_bi%2B:*:*:*:*:*:*:*:*"""
            )
        }
    }
})

private const val ADVISOR_NAME = "VulnerableCode"

private val idLang = Identifier("Maven:org.apache.commons:commons-lang3:3.5")
private val idText = Identifier("Maven:org.apache.commons:commons-text:1.1")
private val idStruts = Identifier("Maven:org.apache.struts:struts2-assembly:2.5.14.1")
private val idJUnit = Identifier("Maven:junit:junit:4.12")
private val idHamcrest = Identifier("Maven:org.hamcrest:hamcrest-core:1.3")
private val idLog4j = Identifier("Maven:org.apache.logging.log4j:log4j-core:2.17.0")

/**
 * The list with the identifiers of packages that are referenced in the test result file.
 */
private val packageIdentifiers = setOf(idJUnit, idLang, idText, idStruts, idHamcrest)

/**
 * The list of packages referenced by the test result. These packages should be requested by the vulnerability provider.
 */
private val packages = packageIdentifiers.map { it.toPurl() }

/**
 * The JSON request to query the test packages found in the result.
 */
private val packagesRequestJson = generatePackagesRequest()

/**
 * Prepare this server to expect a bulk [request] for the test packages and to answer it with the given [responseFile].
 */
private fun WireMockServer.stubPackagesRequest(responseFile: String, request: String = packagesRequestJson) {
    stubFor(
        post(urlPathEqualTo("/packages/bulk_search"))
            .withRequestBody(
                equalToJson(request, /* ignoreArrayOrder = */ true, /* ignoreExtraElements = */ false)
            )
            .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
            .willReturn(
                aResponse().withStatus(200)
                    .withBodyFile(responseFile)
            )
    )
}

/**
 * Create a configuration for the [VulnerableCode] vulnerability provider that points to the local [server].
 */
private fun createConfig(server: WireMockServer): VulnerableCodeConfiguration {
    val url = "http://localhost:${server.port()}"
    return VulnerableCodeConfiguration(url, null, null)
}

/**
 * Create a test instance of [VulnerableCode] that communicates with the local [server].
 */
private fun createVulnerableCode(server: WireMockServer): VulnerableCode = VulnerableCode(config = createConfig(server))

/**
 * Return a list with [Package]s from the analyzer result file that serve as input for the [VulnerableCode] advisor.
 */
private fun TestConfiguration.inputPackagesFromAnalyzerResult(): Set<Package> =
    readResourceValue<OrtResult>("/ort-analyzer-result.yml").getPackages().mapTo(mutableSetOf()) { it.metadata }

/**
 * Return a set with [Package]s to be used as input for the [VulnerableCode] advisor derived from the given
 * [identifiers].
 */
private fun inputPackagesFromIdentifiers(vararg identifiers: Identifier): Set<Package> =
    identifiers.map { Package.EMPTY.copy(id = it, purl = it.toPurl()) }.toSet()

/**
 * Generate the JSON body of the request to query information about the packages identified by the given [purls].
 * The request mainly consists of an array with the package URLs.
 */
private fun generatePackagesRequest(purls: Collection<String> = packages): String =
    purls.joinToString(prefix = "{ \"purls\": [", postfix = "] }") { "\"$it\"" }

/**
 * Generate the JSON body of the request to query vulnerability information about the [Package] with the given [id].
 */
private fun generatePackagesRequest(id: Identifier): String = generatePackagesRequest(listOf(id.toPurl()))
