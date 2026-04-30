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

package org.ossreviewtoolkit.plugins.advisors.scanoss

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

import java.io.IOException
import java.net.URI
import java.time.Instant

import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.clients.scanoss.V2ComponentRequest
import org.ossreviewtoolkit.clients.scanoss.V2ComponentsRequest
import org.ossreviewtoolkit.clients.scanoss.V2Vulnerability
import org.ossreviewtoolkit.clients.scanoss.client.vulnerabilities_GetComponentsVulnerabilities
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProvider
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProviderFactory
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.ort.okHttpClient

/**
 * An [AdviceProvider] implementation that obtains security vulnerability information from a
 * [SCANOSS][https://github.com/aboutcode-org/vulnerablecode] instance.
 */
@OrtPlugin(
    displayName = "SCANOSS",
    description = "An advisor that uses a SCANOSS instance to determine vulnerabilities in dependencies.",
    factory = AdviceProviderFactory::class
)
class ScanOss(
    override val descriptor: PluginDescriptor = ScanOssFactory.descriptor,
    config: ScanOssConfig
) : AdviceProvider {
    private val client = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }

        install(DefaultRequest) {
            url(config.apiUrl)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey.value}")
        }

        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override val details = AdvisorDetails(descriptor.id)

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()
        val issues = mutableListOf<Issue>()

        val purls = packages.associateByTo(mutableMapOf()) { it.purl }
        val input = V2ComponentsRequest(purls.keys.map { V2ComponentRequest(it) })
        val response = client.vulnerabilities_GetComponentsVulnerabilities(input)
            ?: throw IOException("The API endpoint was not found.")

        val allVulnerabilities = response.components.mapNotNull { component ->
            // Use the component's "requirement" instead of "version" as the former exactly echos the input, and has
            // e.g. no "v" prefix for "golang" PURLs.
            val purl = "${component.purl}@${component.requirement}"

            purls.remove(purl)?.let { pkg ->
                val vulns = component.vulnerabilities.mapNotNull { it.toOrtVulnerability() }
                vulns.takeUnless { it.isEmpty() }?.let { pkg to it }
            }
        }

        purls.mapTo(issues) { purl ->
            Issue(
                source = descriptor.displayName,
                message = "The PURL '$purl' could not be mapped to a package."
            )
        }

        val endTime = Instant.now()

        return allVulnerabilities.associate { (pkg, vulnerabilities) ->
            pkg to AdvisorResult(details, AdvisorSummary(startTime, endTime, issues), vulnerabilities)
        }
    }
}

private fun V2Vulnerability.toOrtVulnerability(): Vulnerability? {
    val vulnId = id ?: cve ?: return null
    val infoUrl = url?.let { URI(it) } ?: return null

    return Vulnerability(
        id = vulnId,
        summary = summary,
        // TODO: Get a more detailed description.
        references = cvss.map { info ->
            VulnerabilityReference(
                url = infoUrl,
                scoringSystem = info.cvss?.substringBefore('/', "")?.ifEmpty { null },
                severity = info.cvss_severity,
                score = info.cvss_score,
                vector = info.cvss
            )
        }.ifEmpty {
            listOf(
                VulnerabilityReference(
                    url = infoUrl,
                    scoringSystem = null,
                    severity = severity,
                    score = null,
                    vector = null
                )
            )
        }
    )
}
