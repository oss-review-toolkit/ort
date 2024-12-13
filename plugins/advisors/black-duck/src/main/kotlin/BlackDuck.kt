/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.advisors.blackduck

import com.blackduck.integration.blackduck.api.generated.view.VulnerabilityView

import java.time.Instant

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.vulnerabilities.Cvss2Rating
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.alsoIfNull
import org.ossreviewtoolkit.utils.common.enumSetOf

@OrtPlugin(
    id = "BlackDuck",
    displayName = "BlackDuck",
    description = "An advisor that retrieves vulnerability information from a BlackDuck instance.",
    factory = AdviceProviderFactory::class
)
class BlackDuck(
    override val descriptor: PluginDescriptor,
    private val blackDuckApi: ComponentServiceClient
) : AdviceProvider {
    override val details = AdvisorDetails(descriptor.id, enumSetOf(AdvisorCapability.VULNERABILITIES))

    constructor(descriptor: PluginDescriptor, config: BlackDuckConfiguration) : this(
        descriptor, ExtendedComponentService.create(config.serverUrl, config.apiToken)
    )

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()

        // TODO: run in parallel.
        val result = packages.map { pkg ->
            val vulnerabilities = getVulnerabilitiesByPurl(pkg).orEmpty()

            pkg to AdvisorResult(
                advisor = details,
                summary = AdvisorSummary(
                    startTime = startTime,
                    endTime = Instant.now()
                ),
                vulnerabilities = vulnerabilities
            )
        }.toList().toMap()

        return result
    }

    private fun getVulnerabilitiesByPurl(pkg: Package): List<Vulnerability>? {
        logger.info { "Get vulnerabilities for ${pkg.id.toCoordinates()} by purl: '${pkg.purl}'." }

        val purl = pkg.purl.takeIf { Purl.hasName(it) } ?: run {
            logger.warn { "Skipping invalid purl '$this'." }
            return null
        }

        val searchResults = blackDuckApi.searchKbComponentsByPurl(purl)
        val originViews = searchResults.mapNotNull { searchResult ->
            blackDuckApi.getOriginView(searchResult).alsoIfNull {
                // A purl matches on the granularity of an origin in BlackDuck's speak.
                // So, in theory at most one origin should be matched.
                logger.warn { "Could details for variant '${searchResult.variant}' matched by '${pkg.purl}'." }
            }
        }

        val vulnerabilities = originViews.flatMap { blackDuckApi.getVulnerabilities(it) }.distinctBy { it.name }

        logger.info {
            "Found ${vulnerabilities.size} vulnerabilities by purl $purl for package ${pkg.id.toCoordinates()}'"
        }

        return vulnerabilities.map { it.toOrtVulnerability() }
    }
}

private fun VulnerabilityView.toOrtVulnerability(): Vulnerability {
    val referenceUris = mutableListOf(meta.href.uri()).apply {
        meta.links.mapTo(this) { it.href.uri() }
    }

    val references = referenceUris.map { uri ->
        val cvssVector = cvss3?.vector ?: cvss2?.vector
        val scoringSystem = cvssVector?.substringBefore('/', Cvss2Rating.PREFIXES.first())

        VulnerabilityReference(
            url = uri,
            scoringSystem = scoringSystem,
            severity = severity.toString(),
            score = overallScore.toFloat(),
            vector = cvssVector
        )
    }

    return Vulnerability(
        id = name,
        description = description,
        references = references
    )
}
