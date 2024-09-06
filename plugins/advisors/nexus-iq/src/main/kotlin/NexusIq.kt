/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.advisors.nexusiq

import java.net.URI
import java.time.Duration
import java.time.Instant

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.clients.nexusiq.NexusIqService
import org.ossreviewtoolkit.clients.nexusiq.NexusIqService.Component
import org.ossreviewtoolkit.clients.nexusiq.NexusIqService.ComponentDetails
import org.ossreviewtoolkit.clients.nexusiq.NexusIqService.ComponentsWrapper
import org.ossreviewtoolkit.clients.nexusiq.NexusIqService.SecurityData
import org.ossreviewtoolkit.clients.nexusiq.NexusIqService.SecurityIssue
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.utils.PurlType
import org.ossreviewtoolkit.model.utils.getPurlType
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

/**
 * The number of packages to request from Nexus IQ in one request.
 */
private const val BULK_REQUEST_SIZE = 128

/**
 * The timeout for requests to the NexusIQ REST API. This timeout is larger than the one used by default within ORT,
 * as practice has shown that NexusIQ needs more time to process certain requests.
 */
private val READ_TIMEOUT = Duration.ofSeconds(60)

/**
 * A wrapper for [Nexus IQ Server](https://help.sonatype.com/iqserver) security vulnerability data.
 *
 * This [AdviceProvider] offers the following configuration options:
 *
 * #### [Options][PluginConfiguration.options]
 *
 * * **`serverUrl`:** The URL to use for REST API requests against the server.
 * * **`browseUrl`:** The URL to use as a base for browsing vulnerability details. Defaults to the server URL.
 *
 * #### [Secrets][PluginConfiguration.secrets]
 *
 * * **`username`:** The username to use for authentication.
 * * **`password`:** The password to use for authentication.
 *
 * If not both `username` and `password` are provided, authentication is disabled.
 */
@OrtPlugin(
    id = "NexusIQ",
    displayName = "Nexus IQ",
    description = "An advisor that uses Sonatype's Nexus IQ Server to determine vulnerabilities in dependencies.",
    factory = AdviceProviderFactory::class
)
class NexusIq(override val descriptor: PluginDescriptor, private val config: NexusIqConfiguration) : AdviceProvider {
    override val details = AdvisorDetails(descriptor.id, enumSetOf(AdvisorCapability.VULNERABILITIES))

    private val service by lazy {
        NexusIqService.create(
            config.serverUrl,
            config.username?.value,
            config.password?.value,
            OkHttpClientHelper.buildClient {
                readTimeout(READ_TIMEOUT)
            }
        )
    }

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()

        val components = packages.filter { it.purl.isNotEmpty() }.map { pkg ->
            val packageUrl = buildString {
                append(pkg.purl)

                when (pkg.id.getPurlType()) {
                    PurlType.MAVEN.toString() -> append("?type=jar")
                    PurlType.PYPI.toString() -> append("?extension=tar.gz")
                }
            }

            Component(packageUrl)
        }

        logger.debug { "Querying component details from ${config.serverUrl}." }

        val componentDetails = mutableMapOf<String, ComponentDetails>()
        val issues = mutableListOf<Issue>()

        components.chunked(BULK_REQUEST_SIZE).forEach { chunk ->
            runCatching {
                val results = service.getComponentDetails(ComponentsWrapper(chunk)).componentDetails.associateBy {
                    it.component.packageUrl.substringBefore("?")
                }

                componentDetails += results.filterValues { it.securityData.securityIssues.isNotEmpty() }
            }.onFailure {
                // Create dummy details for all components in the chunk as the current data model does not allow to
                // return issues that are not associated to any package.
                componentDetails += chunk.associate { component ->
                    component.packageUrl to ComponentDetails(component, SecurityData(emptyList()))
                }

                issues += Issue(source = descriptor.displayName, message = it.collectMessages())
            }
        }

        val endTime = Instant.now()

        return packages.mapNotNullTo(mutableListOf()) { pkg ->
            componentDetails[pkg.purl]?.let { pkgDetails ->
                pkg to AdvisorResult(
                    details,
                    AdvisorSummary(startTime, endTime, issues),
                    vulnerabilities = pkgDetails.securityData.securityIssues.map { it.toVulnerability() }
                )
            }
        }.toMap()
    }

    /**
     * Construct a [Vulnerability] from the data stored in this issue.
     */
    private fun SecurityIssue.toVulnerability(): Vulnerability {
        val references = mutableListOf<VulnerabilityReference>()

        val browseUrl = URI("${config.browseUrl}/assets/index.html#/vulnerabilities/$reference")
        val nexusIqReference = VulnerabilityReference(browseUrl, scoringSystem(), threatCategory, severity, null)

        references += nexusIqReference
        url.takeIf { it != browseUrl }?.let { references += nexusIqReference.copy(url = it) }

        return Vulnerability(id = reference, references = references)
    }
}
