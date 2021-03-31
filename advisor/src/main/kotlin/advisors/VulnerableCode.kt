/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

import org.ossreviewtoolkit.advisor.AbstractVulnerabilityProviderFactory
import org.ossreviewtoolkit.advisor.VulnerabilityProvider
import org.ossreviewtoolkit.clients.vulnerablecode.VulnerableCodeService
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.VulnerableCodeConfiguration
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.OkHttpClientHelper

/**
 * A [VulnerabilityProvider] implementation that obtains security vulnerability information from a
 * [VulnerableCode][https://github.com/nexB/vulnerablecode] instance.
 */
class VulnerableCode(
    name: String,
    private val vulnerableCodeConfiguration: VulnerableCodeConfiguration
) : VulnerabilityProvider(name) {
    class Factory : AbstractVulnerabilityProviderFactory<VulnerableCode>("VulnerableCode") {
        override fun create(config: AdvisorConfiguration) =
            VulnerableCode(providerName, config.forProvider { vulnerableCode })
    }

    companion object {
        /**
         * The number of elements to request at once in a bulk request. This value was chosen more or less
         * randomly to keep the size of responses reasonably small.
         */
        private const val BULK_FETCH_SIZE = 100

        /** A severity value used if no information is provided by VulnerableCode. */
        private const val SEVERITY_UNDEFINED = -1f

        /** A regular expression pattern to extract the ID of a vulnerability from its URI. */
        private val vulnerabilityIdPattern = Regex(".*/api/vulnerabilities/([^/]+)/")

        /**
         * Try to extract the (internal VulnerableCode) ID from the given [url] of a vulnerability. Throw an
         * exception if the URL has an unexpected form.
         */
        private fun extractVulnerabilityId(url: String): String =
            vulnerabilityIdPattern.matchEntire(url)?.groups?.get(1)?.value
                ?: throw IOException("Unexpected URL of a vulnerability: $url.")
    }

    private val service by lazy {
        VulnerableCodeService.create(vulnerableCodeConfiguration.serverUrl, OkHttpClientHelper.buildClient())
    }

    override suspend fun retrievePackageVulnerabilities(packages: List<Package>): Map<Package, List<AdvisorResult>> {
        val startTime = Instant.now()

        val components = packages.map { it.purl }

        @Suppress("TooGenericExceptionCaught")
        return try {
            val packagesToVulnerabilityRefs = mutableMapOf<String, VulnerableCodeService.Vulnerabilities>()

            components.chunked(BULK_FETCH_SIZE).forEach { component ->
                val requestResults = service.getPackageVulnerabilities(VulnerableCodeService.PackagesWrapper(component))
                packagesToVulnerabilityRefs += requestResults
            }

            val packagesToVulnerabilities = fetchVulnerabilityDetails(packagesToVulnerabilityRefs, service)

            val endTime = Instant.now()

            packages.mapNotNull { pkg ->
                packagesToVulnerabilities[pkg.id.toPurl()]?.let { vulnerabilities ->
                    pkg to listOf(
                        AdvisorResult(
                            vulnerabilities,
                            AdvisorDetails(providerName),
                            AdvisorSummary(startTime, endTime)
                        )
                    )
                }
            }.toMap()
        } catch (e: Exception) {
            createFailedResults(startTime, packages, e)
        }
    }

    /**
     * Construct a map from package URLs to the vulnerabilities of this package based on a
     * [map with vulnerability references][packagesToVulnerabilityRefs] using the specified [service]. This
     * function looks up the details of the vulnerabilities the references point to.
     */
    private suspend fun fetchVulnerabilityDetails(
        packagesToVulnerabilityRefs: MutableMap<String, VulnerableCodeService.Vulnerabilities>,
        service: VulnerableCodeService
    ): Map<String, List<Vulnerability>> {
        val vulnerabilityIds = packagesToVulnerabilityRefs.values.flatMap { it.unresolvedVulnerabilities }
            .map { it.url }
        val vulnerabilityDetails = resolveVulnerabilities(service, vulnerabilityIds)
        return packagesToVulnerabilityRefs
            .filterNot { it.value.unresolvedVulnerabilities.isEmpty() }
            .mapValues { (_, refs) ->
                refs.unresolvedVulnerabilities.mapNotNull { vulnerabilityDetails[it.url] }
            }
    }

    /**
     * Resolve the given [vulnerabilityUrls] by querying the corresponding vulnerability IDs from the VulnerableCode
     * [service]. Return a map with the URLs keys and the resolved vulnerability details as values.
     */
    private suspend fun resolveVulnerabilities(
        service: VulnerableCodeService,
        vulnerabilityUrls: List<String>
    ): Map<String, Vulnerability> =
        coroutineScope {
            vulnerabilityUrls.map { extractVulnerabilityId(it) }
                .map { async { service.getVulnerability(it) } }
                .awaitAll()
                .map { Vulnerability(it.cveId.orEmpty(), it.cvss ?: SEVERITY_UNDEFINED, URI(it.url)) }
                .associateBy { "${it.url}" }
        }
}
