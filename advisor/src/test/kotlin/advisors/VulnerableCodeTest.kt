/*
 * Copyright (C) 2021 Bosch.IO GmbH
 * Copyright (C) 2021 HERE Europe B.V.
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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File
import java.net.URI

import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.VulnerableCodeConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class VulnerableCodeTest : WordSpec({
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

    "VulnerableCode" should {
        "return vulnerability information" {
            server.stubPackagesRequest("response_packages.json")
            val vulnerableCode = createVulnerableCode(server)
            val packagesToAdvise = inputPackages()

            val result = vulnerableCode.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result.shouldNotBeEmpty()
            result.keys should containExactlyInAnyOrder(idLang, idStruts)

            val langResults = result.getValue(idLang)
            langResults shouldHaveSize(1)
            langResults[0].advisor shouldBe vulnerableCode.details
            val expLangVulnerability = Vulnerability(
                id = "CVE-2014-8242",
                references = listOf(
                    VulnerabilityReference(
                        URI("https://nvd.nist.gov/vuln/detail/CVE-2014-8242"),
                        scoringSystem = null,
                        severity = null
                    ),
                    VulnerabilityReference(
                        URI("https://github.com/apache/commons-lang/security/advisories/GHSA-2cxf-6567-7pp6"),
                        scoringSystem = "cvssv3.1_qr",
                        severity = "LOW"
                    ),
                    VulnerabilityReference(
                        URI("https://github.com/advisories/GHSA-2cxf-6567-7pp6"),
                        scoringSystem = null,
                        severity = null
                    )
                )
            )

            langResults.flatMap { it.vulnerabilities } should containExactly(expLangVulnerability)

            val strutsResults = result.getValue(idStruts)
            strutsResults shouldHaveSize(1)
            val expStrutsVulnerabilities = listOf(
                Vulnerability(
                    id = "CVE-2009-1382",
                    references = listOf(
                        VulnerabilityReference(
                            URI("https://nvd.nist.gov/vuln/detail/CVE-2009-1382"),
                            scoringSystem = "cvssv2",
                            severity = "7"
                        )
                    )
                ),
                Vulnerability(
                    id = "CVE-2019-CoV19",
                    references = listOf(
                        VulnerabilityReference(
                            URI("https://nvd.nist.gov/vuln/detail/CVE-2019-CoV19"),
                            scoringSystem = "cvssv3",
                            severity = "10"
                        ),
                        VulnerabilityReference(
                            URI("https://nvd.nist.gov/vuln/detail/CVE-2019-CoV19"),
                            scoringSystem = "cvssv3.1_qr",
                            severity = "HIGH"
                        )
                    )
                )
            )

            strutsResults.flatMap { it.vulnerabilities } should containExactlyInAnyOrder(expStrutsVulnerabilities)
        }

        "handle a failure response from the server" {
            server.stubFor(
                post(urlPathEqualTo("/api/packages/bulk_search/"))
                    .willReturn(
                        aResponse().withStatus(500)
                    )
            )
            val vulnerableCode = createVulnerableCode(server)
            val packagesToAdvise = inputPackages()

            val result = vulnerableCode.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result shouldNotBeNull {
                keys should containExactly(packageIdentifiers)

                packageIdentifiers.forEach { pkg ->
                    val pkgResults = getValue(pkg)
                    pkgResults shouldHaveSize 1
                    val pkgResult = pkgResults[0]
                    pkgResult.advisor shouldBe vulnerableCode.details
                    pkgResult.vulnerabilities should beEmpty()
                    pkgResult.summary.issues shouldHaveSize 1
                    val issue = pkgResult.summary.issues[0]
                    issue.severity shouldBe Severity.ERROR
                }
            }
        }

        "filter out packages without vulnerabilities" {
            server.stubPackagesRequest("response_packages_no_vulnerabilities.json")
            val vulnerableCode = createVulnerableCode(server)
            val packagesToAdvise = inputPackages()

            val result = vulnerableCode.retrievePackageFindings(packagesToAdvise).mapKeys { it.key.id }

            result.keys should containExactly(idStruts)
        }

        "handle unexpected packages in the query result" {
            server.stubPackagesRequest("response_unexpected_packages.json")
            val vulnerableCode = createVulnerableCode(server)
            val packagesToAdvise = inputPackages()

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
})

private const val ADVISOR_NAME = "VulnerableCodeTestAdvisor"
private const val TEST_FILES_ROOT = "src/test/assets"
private const val TEST_FILES_DIRECTORY = "vulnerable-code"
private const val TEST_RESULT_NAME = "ort-analyzer-result.yml"

private val idLang = Identifier("Maven:org.apache.commons:commons-lang3:3.5")
private val idText = Identifier("Maven:org.apache.commons:commons-text:1.1")
private val idStruts = Identifier("Maven:org.apache.struts:struts2-assembly:2.5.14.1")
private val idJUnit = Identifier("Maven:junit:junit:4.12")
private val idHamcrest = Identifier("Maven:org.hamcrest:hamcrest-core:1.3")

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
 * Prepare this server to expect a bulk request for the test packages and to answer it with the given [responseFile].
 */
private fun WireMockServer.stubPackagesRequest(responseFile: String) {
    stubFor(
        post(urlPathEqualTo("/api/packages/bulk_search"))
            .withRequestBody(equalToJson(packagesRequestJson, false, false))
            .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
            .willReturn(
                aResponse().withStatus(200)
                    .withBodyFile("$TEST_FILES_DIRECTORY/$responseFile")
            )
    )
}

/**
 * Create a configuration for the [VulnerableCode] vulnerability provider that points to the local [server].
 */
private fun createConfig(server: WireMockServer): VulnerableCodeConfiguration {
    val url = "http://localhost:${server.port()}"
    return VulnerableCodeConfiguration(url)
}

/**
 * Create a test instance of [VulnerableCode] that communicates with the local [server].
 */
private fun createVulnerableCode(server: WireMockServer): VulnerableCode =
    VulnerableCode(ADVISOR_NAME, createConfig(server))

/**
 * Return the test file with an analyzer result.
 */
private fun resultFile(): File = File(TEST_FILES_ROOT).resolve(TEST_RESULT_NAME)

/**
 * Return a list with [Package]s from the analyzer result file that serve as input for the [VulnerableCode] advisor.
 */
private fun inputPackages(): List<Package> =
    resultFile().readValue<OrtResult>().getPackages(false).map { it.pkg }

/**
 * Generate the JSON body of the request to query information about packages. It mainly consists of an array with the
 * package URLs.
 */
private fun generatePackagesRequest(): String =
    packages.joinToString(prefix = "{ \"purls\": [", postfix = "] }") { "\"$it\"" }
