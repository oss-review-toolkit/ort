/*
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import org.ossreviewtoolkit.advisor.AbstractVulnerabilityProviderFactory
import org.ossreviewtoolkit.advisor.VulnerabilityProvider
import org.ossreviewtoolkit.clients.nexusiq.NexusIqService
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
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.log

import retrofit2.HttpException

/**
 * The number of packages to request from Nexus IQ in one request.
 */
private const val REQUEST_CHUNK_SIZE = 100

/**
 * A wrapper for [Nexus IQ Server](https://help.sonatype.com/iqserver) security vulnerability data.
 */
class NexusIq(name: String, private val nexusIqConfig: NexusIqConfiguration) : VulnerabilityProvider(name) {
    class Factory : AbstractVulnerabilityProviderFactory<NexusIq>("NexusIQ") {
        override fun create(config: AdvisorConfiguration) = NexusIq(providerName, config.forProvider { nexusIq })
    }

    private val service by lazy {
        NexusIqService.create(
            nexusIqConfig.serverUrl,
            nexusIqConfig.username,
            nexusIqConfig.password,
            OkHttpClientHelper.buildClient()
        )
    }

    override suspend fun retrievePackageVulnerabilities(packages: List<Package>): Map<Package, List<AdvisorResult>> {
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

            components.chunked(REQUEST_CHUNK_SIZE).forEach { chunk ->
                val requestResults = getComponentDetails(service, chunk).componentDetails.associateBy {
                    it.component.packageUrl.substringBefore("?")
                }

                componentDetails += requestResults.filterValues { it.securityData.securityIssues.isNotEmpty() }
            }

            val endTime = Instant.now()

            packages.mapNotNullTo(mutableListOf()) { pkg ->
                componentDetails[pkg.id.toPurl()]?.let { details ->
                    pkg to listOf(
                        AdvisorResult(
                            details.securityData.securityIssues.mapNotNull { it.toVulnerability() },
                            AdvisorDetails(providerName),
                            AdvisorSummary(startTime, endTime)
                        )
                    )
                }
            }.toMap()
        } catch (e: IOException) {
            createFailedResults(startTime, packages, e)
        }
    }

    /**
     * Construct a [Vulnerability] from the data stored in this issue. As a [VulnerabilityReference] requires a
     * non-null URI, issues without an URI yield *null* results. (This is rather a paranoia check, as issues are
     * expected to have a URI.)
     */
    private fun NexusIqService.SecurityIssue.toVulnerability(): Vulnerability? {
        val browseUrl = if (url == null && reference.startsWith(NexusIqService.SONATYPE_PREFIX)) {
            URI("${nexusIqConfig.browseUrl}/assets/index.html#/vulnerabilities/$reference")
        } else {
            url
        }

        return browseUrl?.let { uri ->
            val ref = VulnerabilityReference(uri, scoringSystem(), severity.toString())
            Vulnerability(reference, listOf(ref))
        }
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
