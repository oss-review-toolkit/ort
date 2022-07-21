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

import java.net.URI
import java.time.Instant

import org.ossreviewtoolkit.advisor.AbstractAdviceProviderFactory
import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.clients.vulnerablecode.VulnerableCodeService
import org.ossreviewtoolkit.clients.vulnerablecode.VulnerableCodeService.PackagesWrapper
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.VulnerableCodeConfiguration
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

/**
 * The number of elements to request at once in a bulk request. This value was chosen more or less randomly to keep the
 * size of responses reasonably small.
 */
private const val BULK_REQUEST_SIZE = 100

/**
 * An [AdviceProvider] implementation that obtains security vulnerability information from a
 * [VulnerableCode][https://github.com/nexB/vulnerablecode] instance.
 */
class VulnerableCode(name: String, vulnerableCodeConfiguration: VulnerableCodeConfiguration) : AdviceProvider(name) {
    class Factory : AbstractAdviceProviderFactory<VulnerableCode>("VulnerableCode") {
        override fun create(config: AdvisorConfiguration) =
            VulnerableCode(providerName, config.forProvider { vulnerableCode })
    }

    /**
     * The details returned with each [AdvisorResult] produced by this instance. As this is constant, it can be
     * created once beforehand.
     */
    override val details = AdvisorDetails(providerName, enumSetOf(AdvisorCapability.VULNERABILITIES))

    private val service by lazy {
        VulnerableCodeService.create(vulnerableCodeConfiguration.serverUrl, OkHttpClientHelper.buildClient())
    }

    override suspend fun retrievePackageFindings(packages: List<Package>): Map<Package, List<AdvisorResult>> {
        val startTime = Instant.now()

        return runCatching {
            buildMap {
                packages.chunked(BULK_REQUEST_SIZE).forEach { pkg ->
                    putAll(loadVulnerabilities(pkg, startTime))
                }
            }
        }.getOrElse {
            createFailedResults(startTime, packages, it)
        }
    }

    /**
     * Load vulnerability information for the given [packages] and create a map with results per package using the
     * [startTime].
     */
    private suspend fun loadVulnerabilities(
        packages: List<Package>,
        startTime: Instant
    ): Map<Package, List<AdvisorResult>> {
        val packageMap = packages.associateBy { it.purl }
        val packageVulnerabilities = service.getPackageVulnerabilities(PackagesWrapper(packageMap.keys))

        return packageVulnerabilities.filter { it.unresolvedVulnerabilities.isNotEmpty() }.mapNotNull { pv ->
            packageMap[pv.purl]?.let { pkg ->
                val vulnerabilities = pv.unresolvedVulnerabilities.map { it.toModel() }
                val summary = AdvisorSummary(startTime, Instant.now())
                pkg to listOf(AdvisorResult(details, summary, vulnerabilities = vulnerabilities))
            }
        }.toMap()
    }

    /**
     * Convert this vulnerability from the VulnerableCode data model to a [Vulnerability].
     */
    private fun VulnerableCodeService.Vulnerability.toModel(): Vulnerability =
        Vulnerability(id = vulnerabilityId, references = references.flatMap { it.toModel() })

    /**
     * Convert this reference from the VulnerableCode data model to a list of [VulnerabilityReference] objects.
     * In the VulnerableCode model, the reference can be assigned multiple scores in different scoring systems.
     * For each of these scores, a single [VulnerabilityReference] is created. If no score is available, return a
     * list with a single [VulnerabilityReference] with limited data.
     */
    private fun VulnerableCodeService.VulnerabilityReference.toModel(): List<VulnerabilityReference> {
        val sourceUri = URI(url)
        if (scores.isEmpty()) return listOf(VulnerabilityReference(sourceUri, null, null))
        return scores.map { VulnerabilityReference(sourceUri, it.scoringSystem, it.value) }
    }
}
