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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import java.time.Instant

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.advisor.AdvisorUpdate
import org.ossreviewtoolkit.advisor.logger
import org.ossreviewtoolkit.clients.osv.Ecosystem
import org.ossreviewtoolkit.clients.osv.OsvServiceWrapper
import org.ossreviewtoolkit.clients.osv.Severity
import org.ossreviewtoolkit.clients.osv.VulnerabilitiesForPackageRequest
import org.ossreviewtoolkit.clients.osv.Vulnerability
import org.ossreviewtoolkit.clients.osv.VulnerabilityResult
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.utils.common.Options
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

import us.springett.cvss.Cvss
import kotlin.coroutines.coroutineContext

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
class Osv(name: String, config: OsvConfiguration) : AdviceProvider(name) {
    class Factory : AdviceProviderFactory<OsvConfiguration>("OSV") {
        override fun create(config: OsvConfiguration) = Osv(type, config)

        override fun parseConfig(options: Options, secrets: Options) =
            OsvConfiguration(serverUrl = options["serverUrl"])
    }

    override val details: AdvisorDetails = AdvisorDetails(providerName, enumSetOf(AdvisorCapability.VULNERABILITIES))

    private val service = OsvServiceWrapper(
        serverUrl = config.serverUrl,
        httpClient = OkHttpClientHelper.buildClient()
    )

    override suspend fun execute(packages: Set<Package>): Flow<AdvisorUpdate> =
        flow {
            val startTime = Clock.System.now()

            val vulnerabilityIdsByPackageId = getVulnerabilityIdsForPackages(packages)
            val allVulnerabilityIds = vulnerabilityIdsByPackageId.values.flatten().toSet()

            val allVulnerabilities = mutableMapOf<String, Vulnerability>()
            val allIssues = mutableMapOf<String, Issue>()
            val missingIdsByPackage =
                vulnerabilityIdsByPackageId.mapValuesTo(mutableMapOf()) { it.value.toMutableSet() }

            suspend fun createResult(vulnerabilityIds: List<String>, additionalIssues: List<Issue> = emptyList()) =
                AdvisorResult(
                    advisor = details,
                    summary = AdvisorSummary(
                        startTime = startTime.toJavaInstant(),
                        endTime = Clock.System.now().toJavaInstant(),
                        issues = vulnerabilityIds.mapNotNull { allIssues[it] } + additionalIssues
                    ),
                    vulnerabilities = vulnerabilityIds.mapNotNull { allVulnerabilities[it]?.toOrtVulnerability() }
                )

            // Emit updates for packages without vulnerabilities.
            (packages.map { it.id } - vulnerabilityIdsByPackageId.keys).forEach { pkgId ->
                val advisorResult = createResult(emptyList())
                emit(AdvisorUpdate(providerName, pkgId, advisorResult))
            }

            service.getVulnerabilities(allVulnerabilityIds).collect { result ->
                when (result) {
                    is VulnerabilityResult.Success -> allVulnerabilities[result.id] = result.vulnerability
                    is VulnerabilityResult.Failure ->
                        allIssues[result.id] = Issue(source = providerName, message = result.error)
                }

                missingIdsByPackage.mapValues { it.value -= result.id }

                // Emit updates for packages where all vulnerabilities have been received.
                val completedPackages = missingIdsByPackage.filter { it.value.isEmpty() }.keys
                missingIdsByPackage -= completedPackages

                completedPackages.forEach { pkgId ->
                    val vulnerabilityIds = vulnerabilityIdsByPackageId[pkgId].orEmpty()
                    val advisorResult = createResult(vulnerabilityIds)
                    emit(AdvisorUpdate(providerName, pkgId, advisorResult))
                }
            }

            // Emit update for packages which were not completed for some reason.
            missingIdsByPackage.forEach { (pkgId, ids) ->
                val issues = ids.map {
                    Issue(source = providerName, message = "Did not get result for vulnerability id '$it'.")
                }

                val vulnerabilityIds = vulnerabilityIdsByPackageId[pkgId].orEmpty()
                val advisorResult = createResult(vulnerabilityIds, issues)
                emit(AdvisorUpdate(providerName, pkgId, advisorResult))
            }
        }

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()

        val vulnerabilitiesForPackage = getVulnerabilitiesForPackage(packages)

        return packages.mapNotNull { pkg ->
            val advisorResult = AdvisorResult(
                advisor = details,
                summary = AdvisorSummary(
                    startTime = startTime,
                    endTime = Instant.now()
                ),
                vulnerabilities = vulnerabilitiesForPackage[pkg.id]?.map { it.toOrtVulnerability() }.orEmpty()
            )

            vulnerabilitiesForPackage[pkg.id]?.let { pkg to advisorResult }
        }.toMap()
    }

    private suspend fun getVulnerabilitiesForPackage(packages: Set<Package>): Map<Identifier, List<Vulnerability>> {
        logger.info { "Getting vulnerabilities for ${packages.size} packages." }
        val vulnerabilityIdsForPackageId = getVulnerabilityIdsForPackages(packages)
        val allVulnerabilityIds = vulnerabilityIdsForPackageId.values.flatten().toSet()
        logger.info { "Retrieved ${allVulnerabilityIds.size} vulnerabilities." }
        val vulnerabilityForId = getVulnerabilitiesForIds(allVulnerabilityIds).associateBy { it.id }

        return packages.associate { pkg ->
            pkg.id to vulnerabilityIdsForPackageId[pkg.id].orEmpty().map { vulnerabilityForId.getValue(it) }
        }
    }

    private suspend fun getVulnerabilityIdsForPackages(packages: Set<Package>): Map<Identifier, List<String>> {
        val requests = packages.mapNotNull { pkg ->
            createRequest(pkg)?.let { pkg to it }
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

    private suspend fun getVulnerabilitiesForIds(ids: Set<String>): List<Vulnerability> {
        val result = service.getVulnerabilitiesForIds(ids)

        return result.getOrElse {
            logger.error {
                "Requesting vulnerabilities for IDs failed: ${it.collectMessages()}"
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
        "Conan" -> Ecosystem.CONAN_CENTER
        "Crate" -> Ecosystem.CRATES_IO
        "Gem" -> Ecosystem.RUBY_GEMS
        "Go" -> Ecosystem.GO
        "Hackage" -> Ecosystem.HACKAGE
        "NPM" -> Ecosystem.NPM
        "NuGet" -> Ecosystem.NUGET
        "Maven" -> Ecosystem.MAVEN
        "Pub" -> Ecosystem.PUB
        "PyPI" -> Ecosystem.PYPI
        "Swift" -> Ecosystem.SWIFT_URL
        else -> null
    }

    if (name.isNotBlank() && pkg.id.version.isNotBlank() && !ecosystem.isNullOrBlank()) {
        return VulnerabilitiesForPackageRequest(
            // Do not specify the purl here as it is mutually exclusive with the ecosystem.
            pkg = org.ossreviewtoolkit.clients.osv.Package(
                name = name,
                ecosystem = ecosystem
            ),
            version = pkg.id.version
        )
    }

    // TODO: Support querying vulnerabilities by Git commit hash as described at https://osv.dev/docs/#section/OSV-API.
    //       That would allow to generally support e.g. C / C++ projects that do not use a dedicated package manager
    //       like Conan.

    return null
}

private suspend fun Vulnerability.toOrtVulnerability(): org.ossreviewtoolkit.model.vulnerabilities.Vulnerability {
    // OSV uses a list in order to support multiple representations of the severity using different scoring systems.
    // However, only one representation is actually possible currently, because the enum 'Severity.Type' contains just a
    // single element / scoring system. So, picking first severity is fine, in particular because ORT only supports a
    // single severity representation.
    var (scoringSystem, severity) = severity.firstOrNull()?.let {
        require(it.type == Severity.Type.CVSS_V3) {
            "The severity mapping for type '${it.type}' is not implemented."
        }

        Cvss.fromVector(it.score)?.let { cvss ->
            it.score.substringBefore("/") to "${cvss.calculateScore().baseScore}"
        } ?: run {
            logger.debug { "Could not parse CVSS vector '${it.score}'." }
            null to it.score
        }
    } ?: (null to null)

    val specificSeverity = databaseSpecific?.get("severity")
    if (severity == null && specificSeverity != null) {
        // Fallback to the 'severity' property of the unspecified 'databaseSpecific' object.
        // See also https://github.com/google/osv.dev/issues/484.
        if (specificSeverity is JsonPrimitive) {
            severity = specificSeverity.contentOrNull
        }
    }

    val references = references.mapNotNull { reference ->
        val url = reference.url.trim().let { if (it.startsWith("://")) "https$it" else it }

        url.toUri().onFailure {
            logger.debug { "Could not parse reference URL for vulnerability '$id': ${it.message}." }
        }.map {
            VulnerabilityReference(
                url = it,
                scoringSystem = scoringSystem,
                severity = severity
            )
        }.getOrNull()
    }

    return org.ossreviewtoolkit.model.vulnerabilities.Vulnerability(
        id = id,
        summary = summary,
        description = details,
        references = references
    )
}
