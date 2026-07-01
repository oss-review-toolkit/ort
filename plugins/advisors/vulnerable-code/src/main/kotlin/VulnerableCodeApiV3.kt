/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.vulnerablecode

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

import java.net.URI
import java.time.Instant
import java.util.concurrent.TimeUnit

import kotlin.collections.orEmpty

import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.clients.vulnerablecode.AdvisoryQuery
import org.ossreviewtoolkit.clients.vulnerablecode.AdvisoryReference
import org.ossreviewtoolkit.clients.vulnerablecode.AdvisorySeverity
import org.ossreviewtoolkit.clients.vulnerablecode.AdvisoryV3
import org.ossreviewtoolkit.clients.vulnerablecode.PackageQuery
import org.ossreviewtoolkit.clients.vulnerablecode.PackageV3
import org.ossreviewtoolkit.clients.vulnerablecode.PaginatedAdvisoryV3List
import org.ossreviewtoolkit.clients.vulnerablecode.PaginatedPackageV3List
import org.ossreviewtoolkit.clients.vulnerablecode.client.v3AdvisoriesCreate
import org.ossreviewtoolkit.clients.vulnerablecode.client.v3PackagesCreate
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

/**
 * The default maximum number of advisories returned for a single package by the v3/packages endpoint.
 */
private const val DEFAULT_MAX_ADVISORIES = 100

internal class VulnerableCodeApiV3(
    private val descriptor: PluginDescriptor,
    private val details: AdvisorDetails,
    private val config: VulnerableCodeConfiguration
) : VulnerableCodeApi {
    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true

            engine {
                preconfigured = OkHttpClientHelper.buildClient {
                    if (config.readTimeout != null) readTimeout(config.readTimeout, TimeUnit.SECONDS)
                }
            }

            install(DefaultRequest) {
                url(config.v3BaseUrl())
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.UserAgent, "VCIO_API_AGENT")

                config.apiKey?.value?.also {
                    header(HttpHeaders.Authorization, "Token $it")
                }
            }

            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()

        val purls = packages.mapNotNull { pkg -> pkg.purl.ifEmpty { null } }
        val chunks = purls.chunked(BULK_REQUEST_SIZE)

        val allVulnerabilities = mutableMapOf<String, List<AdvisoryV3>>()
        val issues = mutableListOf<Issue>()

        chunks.forEachIndexed { index, chunk ->
            runCatching {
                allVulnerabilities += client.getAdvisoriesByPackage(chunk, issues)
            }.onFailure {
                it.handleChunkRequestFailure(
                    chunk,
                    index,
                    chunks.size,
                    descriptor.displayName,
                    allVulnerabilities,
                    issues
                )
            }
        }

        val endTime = Instant.now()

        return packages.mapNotNullTo(mutableListOf()) { pkg ->
            allVulnerabilities[pkg.purl]?.let { packageVulnerabilities ->
                val vulnerabilities = packageVulnerabilities.map { it.toModel(issues) }.mergeVulnerabilities()
                val summary = AdvisorSummary(startTime, endTime, issues)
                pkg to AdvisorResult(details, summary, vulnerabilities = vulnerabilities)
            }
        }.toMap()
    }

    /**
     * Retrieve the advisories affecting each package in [purls]. Use v3/packages as the package-to-advisory index and
     * v3/advisories as the source for advisory details. Populate [issues] for advisory references that cannot be mapped
     * to details.
     */
    private suspend fun HttpClient.getAdvisoriesByPackage(
        purls: List<String>,
        issues: MutableList<Issue>
    ): Map<String, List<AdvisoryV3>> {
        val advisoryUidsByPurl = getAllPackageDetails(purls).associate { packageDetails ->
            if (packageDetails.affected_by_vulnerabilities.size >= DEFAULT_MAX_ADVISORIES) {
                issues += createAndLogIssue(
                    descriptor.displayName,
                    "The v3/packages response for '${packageDetails.purl}' contains at least " +
                        "$DEFAULT_MAX_ADVISORIES advisories. There may be additional advisories that were not " +
                        "retrieved.",
                    Severity.WARNING
                )
            }

            packageDetails.purl to packageDetails.affected_by_vulnerabilities.mapTo(mutableSetOf()) {
                it.advisory_uid
            }
        }.filterValues { advisoryUids -> advisoryUids.isNotEmpty() }

        if (advisoryUidsByPurl.isEmpty()) return emptyMap()

        val advisoriesByUid = getAllAdvisories(purls).associateBy { it.advisory_uid }
        val missingAdvisoryUids = advisoryUidsByPurl.values.flatten().toSet() - advisoriesByUid.keys

        missingAdvisoryUids.forEach { advisoryUid ->
            issues += createAndLogIssue(
                descriptor.displayName,
                "The v3/packages response referenced advisory '$advisoryUid', but the v3/advisories response did " +
                    "not contain its details.",
                Severity.WARNING
            )
        }

        return advisoryUidsByPurl.mapValues { (_, advisoryUids) ->
            advisoryUids.mapNotNull { advisoriesByUid[it] }
        }
    }

    /**
     * Retrieve detailed package information for the given [purls].
     */
    private suspend fun HttpClient.getAllPackageDetails(purls: List<String>): List<PackageV3> {
        val request = PackageQuery(purls, details = true)
        var page = v3PackagesCreate(request)

        val packages = page.results.toMutableList()

        while (true) {
            val nextUrl = page.next ?: break

            page = post(nextUrl) {
                setBody(request)
            }.body<PaginatedPackageV3List>()

            packages += page.results
        }

        return packages
    }

    /**
     * Retrieve detailed advisory information for the given package [purls].
     */
    private suspend fun HttpClient.getAllAdvisories(purls: List<String>): List<AdvisoryV3> {
        val request = AdvisoryQuery(purls)
        var page = v3AdvisoriesCreate(request)

        val advisories = page.results.toMutableList()

        while (true) {
            val nextUrl = page.next ?: break

            page = post(nextUrl) {
                setBody(request)
            }.body<PaginatedAdvisoryV3List>()

            advisories += page.results
        }

        return advisories
    }

    /**
     * Convert this advisory from the VulnerableCode data model to a [Vulnerability]. Populate [issues] if this
     * fails.
     */
    private fun AdvisoryV3.toModel(issues: MutableList<Issue>): Vulnerability {
        val normalizedSummary = summary?.ifBlank { null }

        return Vulnerability(
            id = preferredCommonId(),
            // The VulnerableCode API v3 summary is actually a more detailed description of the vulnerability, so use it
            // as description and derive a shorter summary from it.
            summary = normalizedSummary.deriveSummary(),
            description = normalizedSummary,
            references = toReferences(issues)
        )
    }

    /**
     * Convert this advisory from the VulnerableCode data model to a list of [VulnerabilityReference] objects. The
     * advisory contains two fields with relevant information: references and severities. If references are available,
     * use them as source locations and enrich them with the scores from the severities. Only use severities as source
     * locations if the advisory has no references. If there are no entries in either of these fields, create a
     * reference from the advisory's URL. Populate [issues] if this fails.
     */
    private fun AdvisoryV3.toReferences(issues: MutableList<Issue>): List<VulnerabilityReference> {
        val advisoryReferences = references.flatMap { it.toReferences(severities, issues) }

        if (advisoryReferences.isNotEmpty()) return advisoryReferences

        val scoredReferences = severities.mapNotNull { it.toReference(url, issues) }

        return scoredReferences.ifEmpty {
            url.toUri(issues)?.let { listOf(VulnerabilityReference(it, null, null, null, null)) }.orEmpty()
        }
    }

    /**
     * Convert this advisory reference from the VulnerableCode data model to [VulnerabilityReference] objects.
     * Populate [issues] if this fails.
     */
    private fun AdvisoryReference.toReferences(
        severities: List<AdvisorySeverity>,
        issues: MutableList<Issue>
    ): List<VulnerabilityReference> {
        val sourceUri = url.toUri(issues) ?: return emptyList()

        return severities.map { it.toReference(sourceUri) }
            .ifEmpty { listOf(VulnerabilityReference(sourceUri, null, null, null, null)) }
    }

    /**
     * Convert this advisory severity from the VulnerableCode data model to a [VulnerabilityReference] using the
     * severity URL, or [fallbackUrl] if the severity URL is missing. Populate [issues] if the selected URL cannot be
     * converted to a URI.
     */
    private fun AdvisorySeverity.toReference(fallbackUrl: String, issues: MutableList<Issue>): VulnerabilityReference? {
        val sourceUrl = url?.takeUnless { it.isBlank() } ?: fallbackUrl

        return sourceUrl.toUri(issues)?.let { toReference(it) }
    }

    /**
     * Convert this advisory severity from the VulnerableCode data model to a [VulnerabilityReference] object with the
     * given source URI.
     */
    private fun AdvisorySeverity.toReference(sourceUri: URI): VulnerabilityReference {
        val score = value?.toFloatOrNull()
        val textualSeverity = value.takeUnless { score != null }
        val vector = scoring_elements?.ifEmpty { null }

        return VulnerabilityReference(sourceUri, scoring_system.toString(), textualSeverity, score, vector)
    }

    /**
     * Return a meaningful identifier for this vulnerability that can be used in reports. Consider the defined aliases
     * and the advisory ID as candidate identifiers. To resolve the discrepancy between VulnerableCode's multiple
     * identifiers and ORT's single [Vulnerability] identifier, prefer a CVE if one is available. Otherwise, use the
     * advisory ID.
     */
    private fun AdvisoryV3.preferredCommonId(): String {
        val allIds = buildList {
            addAll(aliases)
            add(advisory_id)
        }

        return allIds.firstOrNull { it.startsWith("cve", ignoreCase = true) }
            ?: advisory_id
    }

    /**
     * Merge vulnerabilities with the same ID into a single vulnerability, combining their references.
     */
    private fun Collection<Vulnerability>.mergeVulnerabilities(): List<Vulnerability> =
        groupBy { it.id }.values.map { vulnerabilitiesWithSameId ->
            val references = vulnerabilitiesWithSameId.flatMapTo(mutableSetOf()) { it.references }
            val entry = vulnerabilitiesWithSameId.find { it.summary != null || it.description != null }
                ?: vulnerabilitiesWithSameId.first()

            entry.copy(references = references.toList())
        }

    /**
     * Convert this string to a [URI] object. Populate [issues] if this fails.
     */
    private fun String.toUri(issues: MutableList<Issue>): URI? =
        runCatching { URI(fixupUrlEscaping()) }.onFailure {
            issues += createAndLogIssue(
                descriptor.displayName,
                "Failed to map $this to ORT model due to $it.",
                Severity.HINT
            )
        }.getOrNull()
}
