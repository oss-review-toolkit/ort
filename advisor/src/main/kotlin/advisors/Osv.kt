/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

import java.time.Instant

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

import org.ossreviewtoolkit.advisor.AbstractAdviceProviderFactory
import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.clients.osv.Ecosystem
import org.ossreviewtoolkit.clients.osv.OsvApiClient
import org.ossreviewtoolkit.clients.osv.OsvService
import org.ossreviewtoolkit.clients.osv.Severity
import org.ossreviewtoolkit.clients.osv.VulnerabilitiesForPackageRequest
import org.ossreviewtoolkit.clients.osv.Vulnerability
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.log

import us.springett.cvss.Cvss

/**
 * An advice provider that obtains vulnerability information from OSV.dev https://osv.dev/.
 */
class Osv(name: String, advisorConfiguration: AdvisorConfiguration) : AdviceProvider(name) {
    class Factory : AbstractAdviceProviderFactory<Osv>("OSV") {
        override fun create(config: AdvisorConfiguration) = Osv(providerName, config)
    }

    override val details: AdvisorDetails = AdvisorDetails(providerName, enumSetOf(AdvisorCapability.VULNERABILITIES))

    private val service = OsvService(
        serverUrl = advisorConfiguration.osv?.serverUrl ?: OsvApiClient.SERVER_URL_PRODUCTION,
        httpClient = OkHttpClientHelper.buildClient()
    )

    override suspend fun retrievePackageFindings(packages: List<Package>): Map<Package, List<AdvisorResult>> {
        val startTime = Instant.now()

        val vulnerabilitiesForPackage = getVulnerabilitiesForPackage(packages)

        return packages.associateWith { pkg ->
            vulnerabilitiesForPackage[pkg.id]?.let { vulnerabilities ->
                listOf(
                    AdvisorResult(
                        advisor = details,
                        summary = AdvisorSummary(
                            startTime = startTime,
                            endTime = Instant.now()
                        ),
                        vulnerabilities = vulnerabilities.map { it.toOrtVulnerability() }
                    )
                )
            }.orEmpty()
        }
    }

    private fun getVulnerabilitiesForPackage(packages: List<Package>): Map<Identifier, List<Vulnerability>> {
        val vulnerabilityIdsForPackageId = getVulnerabilityIdsForPackages(packages)
        val allVulnerabilityIds = vulnerabilityIdsForPackageId.values.flatten().toSet()
        val vulnerabilityForId = getVulnerabilitiesForIds(allVulnerabilityIds).associateBy { it.id }

        return packages.associate { pkg ->
            pkg.id to vulnerabilityIdsForPackageId[pkg.id].orEmpty().map { vulnerabilityForId.getValue(it) }
        }
    }

    private fun getVulnerabilityIdsForPackages(packages: List<Package>): Map<Identifier, List<String>> {
        val requests = packages.mapNotNull { pkg ->
            createRequest(pkg)?.let { pkg to it }
        }

        val result = service.getVulnerabilityIdsForPackages(requests.map { it.second })
        val results = mutableListOf<Pair<Identifier, List<String>>>()

        result.map {
            it.mapIndexedTo(results) { i, vulnerabilities ->
                requests[i].first.id to vulnerabilities
            }
        }.onFailure {
            log.error {
                "Requesting vulnerabilities IDs for packages failed: ${result.exceptionOrNull()!!.collectMessages()}"
            }
        }

        return results.toMap()
    }

    private fun getVulnerabilitiesForIds(ids: Set<String>): List<Vulnerability> {
        val result = service.getVulnerabilitiesForIds(ids)

        return result.getOrElse {
            log.error {
                "Requesting vulnerabilities IDs for packages failed: ${result.exceptionOrNull()!!.collectMessages()}"
            }
            emptyList()
        }
    }
}

private fun createRequest(pkg: Package): VulnerabilitiesForPackageRequest? {
    val name = when {
        pkg.id.namespace.isEmpty() -> pkg.id.name
        pkg.id.type == "Composer" -> "${pkg.id.namespace}/${pkg.id.name}"
        else -> "${pkg.id.namespace}:${pkg.id.name}"
    }

    val ecosystem = when (pkg.id.type) {
        "Bower" -> null
        "Composer" -> Ecosystem.PACKAGIST
        "Crate" -> Ecosystem.CRATES_IO
        "Gem" -> Ecosystem.RUBY_GEMS
        "Go" -> Ecosystem.GO
        "NPM" -> Ecosystem.NPM
        "NuGet" -> Ecosystem.NUGET
        "Maven" -> Ecosystem.MAVEN
        "PyPI" -> Ecosystem.PYPI
        else -> null
    }

    if (name.isNotBlank() && pkg.id.version.isNotBlank() && !ecosystem.isNullOrBlank()) {
        return VulnerabilitiesForPackageRequest(
            pkg = org.ossreviewtoolkit.clients.osv.Package(
                name = name,
                ecosystem = ecosystem
            ),
            version = pkg.id.version
        )
    }

    // TODO: Handle C++ projects and / or requesting vulnerabilities by commit-ish.

    return null
}

private fun Vulnerability.toOrtVulnerability(): org.ossreviewtoolkit.model.Vulnerability {
    // OSV uses a list in order to support multiple representations of the severity using different scoring systems.
    // However, only one representation is actually possible currently, because the enum 'Severity.Type' contains just a
    // single element / scoring system. So, picking first severity is fine, in particular because ORT only supports a
    // single severity representation.
    var (scoringSystem, severity) = this.severity.firstOrNull()?.let {
        require(it.type == Severity.Type.CVSS_V3) {
            "The severity mapping for type '${it.type}' is not implemented."
        }

        Cvss.fromVector(it.score)?.let { cvss ->
            // Work around for https://github.com/stevespringett/cvss-calculator/issues/56.
            it.score.substringBefore("/") to "${cvss.calculateScore().baseScore}"
        } ?: run {
            log.debug { "Could not parse CVSS vector '${it.score}'." }
            null to it.score
        }
    } ?: (null to null)

    if (severity == null && databaseSpecific != null) {
        // Fallback to the 'severity' property of the unspecified 'databaseSpecific' object.
        // See also https://github.com/google/osv.dev/issues/484.
        databaseSpecific!!["severity"]?.let {
            if (it is JsonPrimitive) {
                severity = it.contentOrNull
            }
        }
    }

    val references = references.mapNotNull { reference ->
        val url = reference.url.trim().let { if (it.startsWith("://")) "https$it" else it }

        url.toUri().onFailure {
            log.debug { "Could not parse reference URL for vulnerability '$id': ${it.message}." }
        }.map {
            VulnerabilityReference(
                url = it,
                scoringSystem = scoringSystem,
                severity = severity,
            )
        }.getOrNull()
    }

    return org.ossreviewtoolkit.model.Vulnerability(
        id = id,
        summary = summary,
        description = details,
        references = references
    )
}
