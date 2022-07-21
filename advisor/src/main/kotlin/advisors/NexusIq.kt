/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

import java.io.IOException
import java.net.URI
import java.time.Instant

import org.ossreviewtoolkit.advisor.AbstractAdviceProviderFactory
import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.clients.nexusiq.NexusIqService
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.NexusIqConfiguration
import org.ossreviewtoolkit.model.utils.PurlType
import org.ossreviewtoolkit.model.utils.getPurlType
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.log

import retrofit2.HttpException

/**
 * The number of packages to request from Nexus IQ in one request.
 */
private const val BULK_REQUEST_SIZE = 128

/**
 * A wrapper for [Nexus IQ Server](https://help.sonatype.com/iqserver) security vulnerability data.
 */
class NexusIq(name: String, private val nexusIqConfig: NexusIqConfiguration) : AdviceProvider(name) {
    class Factory : AbstractAdviceProviderFactory<NexusIq>("NexusIQ") {
        override fun create(config: AdvisorConfiguration) = NexusIq(providerName, config.forProvider { nexusIq })
    }

    override val details: AdvisorDetails = AdvisorDetails(providerName, enumSetOf(AdvisorCapability.VULNERABILITIES))

    private val service by lazy {
        NexusIqService.create(
            nexusIqConfig.serverUrl,
            nexusIqConfig.username,
            nexusIqConfig.password,
            OkHttpClientHelper.buildClient()
        )
    }

    override suspend fun retrievePackageFindings(packages: List<Package>): Map<Package, List<AdvisorResult>> {
        val startTime = Instant.now()

        val components = packages.map { pkg ->
            val packageUrl = buildString {
                append(pkg.purl)

                when (pkg.id.getPurlType()) {
                    PurlType.MAVEN.toString() -> append("?type=jar")
                    PurlType.PYPI.toString() -> append("?extension=tar.gz")
                }
            }

            NexusIqService.Component(packageUrl)
        }

        return try {
            val componentDetails = mutableMapOf<String, NexusIqService.ComponentDetails>()

            components.chunked(BULK_REQUEST_SIZE).forEach { chunk ->
                val requestResults = getComponentDetails(service, chunk).componentDetails.associateBy {
                    it.component.packageUrl.substringBefore("?")
                }

                componentDetails += requestResults.filterValues { it.securityData.securityIssues.isNotEmpty() }
            }

            val endTime = Instant.now()

            packages.mapNotNullTo(mutableListOf()) { pkg ->
                componentDetails[pkg.id.toPurl()]?.let { pkgDetails ->
                    pkg to listOf(
                        AdvisorResult(
                            details,
                            AdvisorSummary(startTime, endTime),
                            vulnerabilities = pkgDetails.securityData.securityIssues.map { it.toVulnerability() }
                        )
                    )
                }
            }.toMap()
        } catch (e: IOException) {
            createFailedResults(startTime, packages, e)
        }
    }

    /**
     * Construct a [Vulnerability] from the data stored in this issue.
     */
    private fun NexusIqService.SecurityIssue.toVulnerability(): Vulnerability {
        val references = mutableListOf<VulnerabilityReference>()

        val browseUrl = URI("${nexusIqConfig.browseUrl}/assets/index.html#/vulnerabilities/$reference")
        val nexusIqReference = VulnerabilityReference(browseUrl, scoringSystem(), severity.toString())

        references += nexusIqReference
        url.takeIf { it != browseUrl }?.let { references += nexusIqReference.copy(url = it) }

        return Vulnerability(id = reference, references = references)
    }

    /**
     * Invoke the [NexusIQ service][service] to request detail information for the given [components]. Catch HTTP
     * exceptions thrown by the service and re-throw them as [IOException].
     */
    private suspend fun getComponentDetails(
        service: NexusIqService,
        components: List<NexusIqService.Component>
    ): NexusIqService.ComponentDetailsWrapper =
        try {
            log.debug { "Querying component details from ${nexusIqConfig.serverUrl}." }
            service.getComponentDetails(NexusIqService.ComponentsWrapper(components))
        } catch (e: HttpException) {
            throw IOException(e)
        }
}
