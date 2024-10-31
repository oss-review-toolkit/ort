/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.advisors.osv

import java.time.Instant

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

import org.apache.logging.log4j.kotlin.logger

import org.metaeffekt.core.security.cvss.CvssVector

import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.clients.osv.OsvServiceWrapper
import org.ossreviewtoolkit.clients.osv.VulnerabilitiesForPackageRequest
import org.ossreviewtoolkit.clients.osv.Vulnerability
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

/**
 * An advice provider that obtains vulnerability information from Open Source Vulnerabilities (https://osv.dev/).
 *
 * This [AdviceProvider] offers the following configuration options:
 *
 * #### [Options][PluginConfiguration.options]
 *
 * * **`serverUrl`:** The base URL of the OSV REST API. If undefined, default is the production endpoint of the official
 *   OSV.dev API.
 */
@OrtPlugin(
    id = "OSV",
    displayName = "OSV",
    description = "An advisor that retrieves vulnerability information from the Open Source Vulnerabilities database.",
    factory = AdviceProviderFactory::class
)
class Osv(override val descriptor: PluginDescriptor, config: OsvConfiguration) : AdviceProvider {
    override val details = AdvisorDetails(descriptor.id, enumSetOf(AdvisorCapability.VULNERABILITIES))

    private val service = OsvServiceWrapper(
        serverUrl = config.serverUrl,
        httpClient = OkHttpClientHelper.buildClient()
    )

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()

        val vulnerabilitiesForPackage = getVulnerabilitiesForPackage(packages)

        return packages.mapNotNull { pkg ->
            vulnerabilitiesForPackage[pkg.id]?.let { vulnerabilities ->
                pkg to AdvisorResult(
                    advisor = details,
                    summary = AdvisorSummary(
                        startTime = startTime,
                        endTime = Instant.now()
                    ),
                    vulnerabilities = vulnerabilities.map { it.toOrtVulnerability() }
                )
            }
        }.toMap()
    }

    private fun getVulnerabilitiesForPackage(packages: Set<Package>): Map<Identifier, List<Vulnerability>> {
        val vulnerabilityIdsForPackageId = getVulnerabilityIdsForPackages(packages)
        val allVulnerabilityIds = vulnerabilityIdsForPackageId.values.flatten().toSet()
        val vulnerabilityForId = getVulnerabilitiesForIds(allVulnerabilityIds).associateBy { it.id }

        return packages.associate { pkg ->
            pkg.id to vulnerabilityIdsForPackageId[pkg.id].orEmpty().map { vulnerabilityForId.getValue(it) }
        }
    }

    private fun getVulnerabilityIdsForPackages(packages: Set<Package>): Map<Identifier, List<String>> {
        val requests = packages.map { pkg ->
            // TODO: Support querying vulnerabilities by Git commit hash as described at
            //       https://google.github.io/osv.dev/post-v1-query/. That would allow to generally support e.g. C / C++
            //       projects that do not use a dedicated package manager, like Conan.
            val request = VulnerabilitiesForPackageRequest(
                pkg = org.ossreviewtoolkit.clients.osv.Package(purl = pkg.purl)
            )

            pkg to request
        }

        val result = service.getVulnerabilityIdsForPackages(requests.map { it.second })
        val results = mutableListOf<Pair<Identifier, List<String>>>()

        result.map { allVulnerabilities ->
            // OSV returns vulnerability results in the same order as packages were requested, so use the list index to
            // identify to which package a result belongs. This means that also empty results are returned as otherwise
            // list indices would not match, so filter these out.
            allVulnerabilities.mapIndexedNotNullTo(results) { i, pkgVulnerabilities ->
                pkgVulnerabilities.takeUnless { it.isEmpty() }?.let { requests[i].first.id to it }
            }
        }.onFailure {
            logger.error {
                "Requesting vulnerability IDs for packages failed: ${it.collectMessages()}"
            }
        }

        return results.toMap()
    }

    private fun getVulnerabilitiesForIds(ids: Set<String>): List<Vulnerability> {
        val result = service.getVulnerabilitiesForIds(ids)

        return result.getOrElse {
            logger.error {
                "Requesting vulnerabilities for IDs failed: ${it.collectMessages()}"
            }

            emptyList()
        }
    }
}

private fun Vulnerability.toOrtVulnerability(): org.ossreviewtoolkit.model.vulnerabilities.Vulnerability {
    // The ORT and OSV vulnerability data models are different in that ORT uses a severity for each reference (assuming
    // that different references could use different severities), whereas OSV manages severities and references on the
    // same level, which means it is not possible to identify whether a reference belongs to a specific severity.
    // To map between these different model, simply use the "cartesian product" to create an ORT reference for each
    // combination of an OSV severity and reference.
    val ortReferences = mutableListOf<VulnerabilityReference>()

    severity.map {
        it.type.name to it.score
    }.ifEmpty {
        listOf(null to null)
    }.forEach { (scoringSystem, severity) ->
        references.mapNotNullTo(ortReferences) { reference ->
            val url = reference.url.trim().let { if (it.startsWith("://")) "https$it" else it }

            url.toUri().onFailure {
                logger.debug { "Could not parse reference URL for vulnerability '$id': ${it.collectMessages()}." }
            }.map {
                // Use the 'severity' property of the unspecified 'databaseSpecific' object.
                // See also https://github.com/google/osv.dev/issues/484.
                val specificSeverity = databaseSpecific?.get("severity")

                val baseScore = runCatching {
                    CvssVector.parseVector(severity)?.baseScore?.toFloat()
                }.onFailure {
                    logger.debug { "Unable to parse CVSS vector '$severity': ${it.collectMessages()}." }
                }.getOrNull()

                val severityRating = (specificSeverity as? JsonPrimitive)?.contentOrNull
                    ?: VulnerabilityReference.getQualitativeRating(scoringSystem, baseScore)?.name

                VulnerabilityReference(it, scoringSystem, severityRating, baseScore, severity)
            }.getOrNull()
        }
    }

    return org.ossreviewtoolkit.model.vulnerabilities.Vulnerability(
        id = id,
        summary = summary,
        description = details,
        references = ortReferences
    )
}
