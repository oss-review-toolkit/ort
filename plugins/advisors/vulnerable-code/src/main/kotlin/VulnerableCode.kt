/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

import java.net.URI
import java.time.Instant
import java.util.concurrent.TimeUnit

import kotlin.collections.orEmpty
import kotlin.coroutines.cancellation.CancellationException

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.vulnerablecode.AdvisoryReference
import org.ossreviewtoolkit.clients.vulnerablecode.AdvisorySeverity
import org.ossreviewtoolkit.clients.vulnerablecode.AffectedByAdvisoryV3
import org.ossreviewtoolkit.clients.vulnerablecode.PackageQuery
import org.ossreviewtoolkit.clients.vulnerablecode.PaginatedPurlList
import org.ossreviewtoolkit.clients.vulnerablecode.client.v3AffectedByAdvisoriesList
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
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProvider
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProviderFactory
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.percentEncode
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

/**
 * The number of elements to request at once in a bulk request for the v3/packages endpoint. The request uses
 * "details=false", so the response only contains a list of matching PURLs that are affected by/fixing vulnerabilities.
 * A relatively large chunk size reduces the number of top-level bulk requests while still keeping individual request
 * bodies reasonably small.
 */
private const val BULK_REQUEST_SIZE = 1000

/**
 * The maximum length for the summary as derived from the description of a vulnerability.
 */
private const val MAX_SUMMARY_LENGTH = 64

/**
 * An [AdviceProvider] implementation that obtains security vulnerability information from a
 * [VulnerableCode](https://github.com/aboutcode-org/vulnerablecode) instance.
 */
@OrtPlugin(
    displayName = "VulnerableCode",
    summary = "An advisor that uses a VulnerableCode instance to determine vulnerabilities in dependencies.",
    factory = AdviceProviderFactory::class
)
class VulnerableCode(
    override val descriptor: PluginDescriptor = VulnerableCodeFactory.descriptor,
    config: VulnerableCodeConfiguration
) : AdviceProvider {
    /**
     * The details returned with each [AdvisorResult] produced by this instance. As this is constant, it can be
     * created once beforehand.
     */
    override val details = AdvisorDetails(descriptor.id)

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true

            engine {
                preconfigured = OkHttpClientHelper.buildClient {
                    if (config.readTimeout != null) readTimeout(config.readTimeout, TimeUnit.SECONDS)
                }
            }

            install(DefaultRequest) {
                url(config.serverUrl)
                header(HttpHeaders.ContentType, ContentType.Application.Json)

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

        val allVulnerabilities = mutableMapOf<String, List<AffectedByAdvisoryV3>>()
        val issues = mutableListOf<Issue>()

        chunks.forEachIndexed { index, chunk ->
            runCatching {
                val request = PackageQuery(chunk, details = false)
                var page = client.v3PackagesCreate(request)
                val queriedPurls = page.results.toMutableSet()

                while (true) {
                    val nextUrl = page.next ?: break

                    page = client.post(nextUrl) {
                        setBody(request)
                    }.body<PaginatedPurlList>()

                    queriedPurls += page.results
                }

                val chunkVulnerabilities = client.getAffectedByAdvisories(queriedPurls.filter { it in chunk })

                allVulnerabilities += chunkVulnerabilities
            }.onFailure {
                if (it is CancellationException) currentCoroutineContext().ensureActive()

                // Create dummy entries for all packages in the chunk as the current data model does not allow to return
                // issues that are not associated to any package.
                allVulnerabilities += chunk.associateWith { emptyList() }

                issues += Issue(source = descriptor.displayName, message = it.collectMessages())

                logger.error {
                    "The request of chunk ${index + 1} of ${chunks.size} failed for the following ${chunk.size} " +
                        "PURL(s):"
                }

                chunk.forEach(logger::error)
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
     * Retrieve all advisories affecting the given [purls]. Filter out packages that are not affected by any advisory.
     */
    private suspend fun HttpClient.getAffectedByAdvisories(
        purls: Collection<String>
    ): Map<String, List<AffectedByAdvisoryV3>> =
        purls.associateWith { purl -> getAllAffectedByAdvisories(purl) }
            .filterValues { advisories -> advisories.isNotEmpty() }

    /**
     * Retrieve all advisories affecting the given [purl].
     */
    private suspend fun HttpClient.getAllAffectedByAdvisories(purl: String): List<AffectedByAdvisoryV3> {
        var page = v3AffectedByAdvisoriesList {
            parameter("purl", purl)
        }

        val advisories = page.results.toMutableList()

        while (true) {
            val nextUrl = page.next ?: break

            page = get(nextUrl).body()
            advisories += page.results
        }

        return advisories
    }

    /**
     * Convert this advisory from the VulnerableCode data model to a [Vulnerability]. Populate [issues] if this
     * fails.
     */
    private fun AffectedByAdvisoryV3.toModel(issues: MutableList<Issue>): Vulnerability {
        val normalizedSummary = summary?.ifBlank { null }

        return Vulnerability(
            id = preferredCommonId(),
            // The VulnerableCode API v3 summary is actually a more detailed description of the vulnerability, so use it
            // as description and derive a shorter summary from it.
            summary = normalizedSummary?.take(MAX_SUMMARY_LENGTH)?.let {
                if (it.length < normalizedSummary.length) "$it..." else it
            },
            description = normalizedSummary,
            references = toReferences(issues)
        )
    }

    /**
     * Convert this advisory from the VulnerableCode data model to a list of [VulnerabilityReference] objects. The
     * advisory contains two fields that contain the relevant information, references and severities, which are both
     * converted to [VulnerabilityReference] objects. If there are no entries in either of these fields, a reference is
     * created from the advisory's URL. Populate [issues] if this fails.
     */
    private fun AffectedByAdvisoryV3.toReferences(issues: MutableList<Issue>): List<VulnerabilityReference> {
        val advisoryReferences = references.mapNotNull { it.toModel(issues) }
        val scoredReferences = severities.mapNotNull { it.toModel(url, issues) }

        return (advisoryReferences + scoredReferences).ifEmpty {
            url.toUri(issues)?.let { listOf(VulnerabilityReference(it, null, null, null, null)) }.orEmpty()
        }
    }

    /**
     * Convert this advisory reference from the VulnerableCode data model to a [VulnerabilityReference] object.
     * Populate [issues] if this fails.
     */
    private fun AdvisoryReference.toModel(issues: MutableList<Issue>): VulnerabilityReference? =
        url.toUri(issues)?.let { VulnerabilityReference(it, null, null, null, null) }

    /**
     * Convert this advisory severity from the VulnerableCode data model to a [VulnerabilityReference] object.
     * Populate [issues] if this fails.
     */
    private fun AdvisorySeverity.toModel(fallbackUrl: String, issues: MutableList<Issue>): VulnerabilityReference? {
        val score = value?.toFloatOrNull()
        val textualSeverity = value.takeUnless { score != null }
        val vector = scoring_elements?.ifEmpty { null }
        val sourceUrl = url?.takeUnless { it.isBlank() } ?: fallbackUrl

        return sourceUrl.toUri(issues)?.let {
            VulnerabilityReference(it, scoring_system, textualSeverity, score, vector)
        }
    }

    /**
     * Return a meaningful identifier for this vulnerability that can be used in reports. Consider the defined aliases
     * and the last path segment of the advisory ID as candidate identifiers, because the advisory ID often embeds a
     * public identifier such as a GHSA or CVE in its final path segment. To resolve the discrepancy between
     * VulnerableCode's multiple identifiers and ORT's single [Vulnerability] identifier, prefer a CVE if one is
     * available. Otherwise, use the last path segment of the advisory ID.
     */
    private fun AffectedByAdvisoryV3.preferredCommonId(): String {
        val advisoryIdSegment = advisory_id.substringAfterLast('/')
        val allIds = buildList {
            addAll(aliases)
            add(advisoryIdSegment)
        }

        return allIds.firstOrNull { it.startsWith("cve", ignoreCase = true) }
            ?: advisoryIdSegment
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
            issues += createAndLogIssue("Failed to map $this to ORT model due to $it.", Severity.HINT)
        }.getOrNull()
}

private val BACKSLASH_ESCAPE_REGEX = """\\\\?(.)""".toRegex()

internal fun String.fixupUrlEscaping(): String =
    replace("""\/""", "/").replace(BACKSLASH_ESCAPE_REGEX) {
        it.groupValues[1].percentEncode()
    }
