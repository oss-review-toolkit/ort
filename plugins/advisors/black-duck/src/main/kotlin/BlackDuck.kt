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

import com.blackduck.integration.blackduck.api.generated.view.OriginView
import com.blackduck.integration.blackduck.api.generated.view.VulnerabilityView

import java.time.Instant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.vulnerabilities.Cvss2Rating
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.enumSetOf

/**
 * This advice provider by default retrieves vulnerabilities by the purl corresponding to the package. If a package has
 * the label [BlackDuck.PACKAGE_LABEL_BLACK_DUCK_ORIGIN_ID] set, then the vulnerabilities are retrieved by that
 * origin-id instead of by the purl.
 */
@OrtPlugin(
    displayName = "Black Duck",
    description = "An advisor that retrieves vulnerability information from a Black Duck instance.",
    factory = AdviceProviderFactory::class
)
class BlackDuck(
    override val descriptor: PluginDescriptor,
    private val blackDuckApi: ComponentServiceClient
) : AdviceProvider {
    companion object {
        /**
         * The key of the package label for specifying the Black Duck origin-id in the form
         * "$externalNamespace:$externalId", see also [BlackDuckOriginId.parse].
         */
        const val PACKAGE_LABEL_BLACK_DUCK_ORIGIN_ID = "black-duck:origin-id"
    }

    override val details = AdvisorDetails(descriptor.id, enumSetOf(AdvisorCapability.VULNERABILITIES))

    constructor(descriptor: PluginDescriptor, config: BlackDuckConfiguration) : this(
        descriptor, ExtendedComponentService.create(config.serverUrl, config.apiToken.value)
    )

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()
        val issuesForId = packages.associate { it.id to mutableListOf<Issue>() }

        logger.info { "Obtaining origins for ${packages.size} package(s)..." }

        val originsForId = withContext(Dispatchers.IO.limitedParallelism(20)) {
            packages.associate { pkg ->
                pkg.id to async { getOrigins(pkg, issuesForId.getValue(pkg.id)) }
            }
        }.mapValues { it.value.await() }

        logger.info { "Obtaining vulnerabilities for ${originsForId.entries.sumOf { it.value.size } } origins..." }

        val vulnerabilitiesForId = withContext(Dispatchers.IO.limitedParallelism(20)) {
            originsForId.mapValues { (id, origins) ->
                async { getVulnerabilities(origins, issuesForId.getValue(id)) }
            }
        }.mapValues { it.value.await() }

        logger.info { originsForId.getSummary() }

        return packages.associateWith { pkg ->
            AdvisorResult(
                details,
                summary = AdvisorSummary(
                    startTime,
                    Instant.now(),
                    issuesForId.getValue(pkg.id)
                ),
                vulnerabilities = vulnerabilitiesForId.getValue(pkg.id).map { it.toOrtVulnerability() }
            )
        }
    }

    private fun getOrigins(pkg: Package, issues: MutableList<Issue>): List<OriginView> {
        val externalId = runCatching {
            pkg.blackDuckOriginId?.let { BlackDuckOriginId.parse(it).toExternalId() }
        }.getOrElse {
            issues += createAndLogIssue(
                source = descriptor.displayName,
                message = "Could not parse origin-id '${pkg.blackDuckOriginId}' for '${pkg.id.toCoordinates()}: " +
                    it.collectMessages()
            )
            return emptyList()
        }

        val searchResults = if (externalId != null) {
            runCatching {
                blackDuckApi.searchKbComponentsByExternalId(externalId)
            }.getOrElse {
                issues += createAndLogIssue(
                    source = descriptor.displayName,
                    message = "Requesting origins for externalId '$externalId' failed: ${it.collectMessages()}"
                )
                return emptyList()
            }
        } else {
            runCatching {
                blackDuckApi.searchKbComponentsByPurl(pkg.purl)
            }.getOrElse {
                issues += createAndLogIssue(
                    source = descriptor.displayName,
                    message = "Requesting origins for purl ${pkg.purl} failed: ${it.collectMessages()}"
                )
                return emptyList()
            }
        }

        val origins = searchResults.mapNotNull { searchResult ->
            runCatching {
                blackDuckApi.getOriginView(searchResult)
            }.onFailure {
                issues += createAndLogIssue(
                    source = descriptor.displayName,
                    message = "Requesting origin details failed: ${it.collectMessages()}"
                )
            }.getOrNull()
        }

        if (origins.isEmpty()) {
            logger.info { "No origin found for package '${pkg.id.toCoordinates()}' (${pkg.requestParam})." }
        } else {
            logger.info {
                "Found ${origins.size} origin(s) for package '${pkg.id.toCoordinates()}' (${pkg.requestParam}): " +
                    "${origins.joinToString { it.identifier }}."
            }
        }

        if (externalId != null && origins.isEmpty()) {
            issues += createAndLogIssue(
                source = descriptor.displayName,
                message = "The origin-id '${pkg.blackDuckOriginId} of package ${pkg.id.toCoordinates()} does not " +
                    "match any origin.",
                severity = Severity.WARNING
            )
        }

        return origins
    }

    private fun getVulnerabilities(
        origins: Collection<OriginView>,
        issues: MutableList<Issue>
    ): List<VulnerabilityView> =
        origins.flatMap { origin ->
            runCatching {
                blackDuckApi.getVulnerabilities(origin)
            }.onSuccess {
                logger.info { "Found ${it.size} vulnerabilities for origin ${origin.identifier}." }
            }.onFailure {
                issues += createAndLogIssue(
                    source = descriptor.displayName,
                    message = "Requesting vulnerabilities for origin ${origin.identifier} failed: " +
                        it.collectMessages()
                )
            }.getOrDefault(emptyList())
        }
}

internal fun VulnerabilityView.toOrtVulnerability(): Vulnerability {
    val referenceUris = setOf(meta.href.uri(), *meta.links.map { it.href.uri() }.toTypedArray())
    val cvssVector = cvss3?.vector ?: cvss2?.vector
    // Only CVSS version 2 vectors do not contain the "CVSS:" label and version prefix.
    val scoringSystem = cvssVector?.substringBefore('/', Cvss2Rating.PREFIXES.first())

    val references = referenceUris.map { uri ->
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

private val OriginView.identifier get() = "$externalNamespace:$externalId"

private fun Map<Identifier, List<OriginView>>.getSummary(): String =
    buildString {
        val idsWithMultipleOrigins = entries.filter { it.value.size > 1 }.sortedBy { it.key }
        if (idsWithMultipleOrigins.isNotEmpty()) {
            appendLine("The following ${idsWithMultipleOrigins.size} packages have multiple matching origins:")
            idsWithMultipleOrigins.forEach { (id, origins) ->
                appendLine("  ${id.toCoordinates()} -> ${origins.joinToString { it.identifier }}")
            }
        }

        val idsWithoutOrigins = entries.filter { it.value.isEmpty() }.map { it.key }.sorted()
        if (idsWithoutOrigins.isNotEmpty()) {
            appendLine("The following ${idsWithoutOrigins.size} packages do not have any matching origin:")
            idsWithoutOrigins.forEach {
                appendLine("  ${it.toCoordinates()}")
            }
        }
    }

private val Package.blackDuckOriginId: String? get() = labels[BlackDuck.PACKAGE_LABEL_BLACK_DUCK_ORIGIN_ID]

private val Package.requestParam: String get() =
    blackDuckOriginId?.let { "origin-id: '$it'" } ?: "purl: '$purl'"
