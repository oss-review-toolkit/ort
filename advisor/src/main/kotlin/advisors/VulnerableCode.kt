/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.clients.vulnerablecode.VulnerableCodeService
import org.ossreviewtoolkit.clients.vulnerablecode.VulnerableCodeService.PackagesWrapper
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.common.Options
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.common.percentEncode
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

/**
 * The number of elements to request at once in a bulk request. This value was chosen more or less randomly to keep the
 * size of responses reasonably small.
 */
private const val BULK_REQUEST_SIZE = 100

/**
 * An [AdviceProvider] implementation that obtains security vulnerability information from a
 * [VulnerableCode][https://github.com/nexB/vulnerablecode] instance.
 *
 * This [AdviceProvider] offers the following configuration options:
 *
 * #### [Options][PluginConfiguration.options]
 *
 * * **`serverUrl`:** The base URL of the VulnerableCode REST API. By default, the public VulnerableCode instance is
 *   used.
 *
 * #### [Secrets][PluginConfiguration.secrets]
 *
 * * **`apiKey`:** The optional API key to use.
 */
class VulnerableCode(name: String, config: VulnerableCodeConfiguration) : AdviceProvider(name) {
    class Factory : AdviceProviderFactory<VulnerableCodeConfiguration>("VulnerableCode") {
        override fun create(config: VulnerableCodeConfiguration) = VulnerableCode(type, config)

        override fun parseConfig(options: Options, secrets: Options) =
            VulnerableCodeConfiguration(
                serverUrl = options["serverUrl"],
                apiKey = secrets["apiKey"]
            )
    }

    /**
     * The details returned with each [AdvisorResult] produced by this instance. As this is constant, it can be
     * created once beforehand.
     */
    override val details = AdvisorDetails(providerName, enumSetOf(AdvisorCapability.VULNERABILITIES))

    private val service by lazy {
        VulnerableCodeService.create(
            config.serverUrl, config.apiKey, OkHttpClientHelper.buildClient()
        )
    }

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()

        val purls = packages.mapNotNull { pkg -> pkg.purl.takeUnless { it.isEmpty() } }

        val allVulnerabilities = mutableMapOf<String, List<VulnerableCodeService.Vulnerability>>()
        val issues = mutableListOf<Issue>()

        purls.chunked(BULK_REQUEST_SIZE).forEach { chunk ->
            runCatching {
                val chunkVulnerabilities = service.getPackageVulnerabilities(PackagesWrapper(chunk)).filter {
                    it.unresolvedVulnerabilities.isNotEmpty()
                }

                allVulnerabilities += chunkVulnerabilities.associate { it.purl to it.unresolvedVulnerabilities }
            }.onFailure {
                // Create dummy entries for all packages in the chunk as the current data model does not allow to return
                // issues that are not associated to any package.
                allVulnerabilities += chunk.associateWith { emptyList() }

                issues += Issue(source = providerName, message = it.collectMessages())
            }
        }

        val endTime = Instant.now()

        return packages.mapNotNullTo(mutableListOf()) { pkg ->
            allVulnerabilities[pkg.id.toPurl()]?.let { packageVulnerabilities ->
                val vulnerabilities = packageVulnerabilities.map { it.toModel(issues) }
                val summary = AdvisorSummary(startTime, endTime, issues)
                pkg to AdvisorResult(details, summary, vulnerabilities = vulnerabilities)
            }
        }.toMap()
    }

    /**
     * Convert this vulnerability from the VulnerableCode data model to a [Vulnerability]. Populate [issues] if this
     * fails.
     */
    private fun VulnerableCodeService.Vulnerability.toModel(issues: MutableList<Issue>): Vulnerability =
        Vulnerability(id = preferredCommonId(), references = references.flatMap { it.toModel(issues) })

    /**
     * Convert this reference from the VulnerableCode data model to a list of [VulnerabilityReference] objects.
     * In the VulnerableCode model, the reference can be assigned multiple scores in different scoring systems.
     * For each of these scores, a single [VulnerabilityReference] is created. If no score is available, return a
     * list with a single [VulnerabilityReference] with limited data. Populate [issues] in case of a failure,
     * e.g. if the conversion to a URI fails.
     */
    private fun VulnerableCodeService.VulnerabilityReference.toModel(
        issues: MutableList<Issue>
    ): List<VulnerabilityReference> =
        runCatching {
            val sourceUri = URI(url.fixupUrlEscaping())
            if (scores.isEmpty()) return listOf(VulnerabilityReference(sourceUri, null, null))
            return scores.map {
                // VulnerableCode returns MODERATE instead of MEDIUM in case of cvssv3.1_qr, see:
                // https://github.com/nexB/vulnerablecode/issues/1186
                val severity = if (it.scoringSystem == "cvssv3.1_qr" && it.value == "MODERATE") "MEDIUM" else it.value

                VulnerabilityReference(sourceUri, it.scoringSystem, severity)
            }
        }.onFailure {
            issues += createAndLogIssue(providerName, "Failed to map $this to ORT model due to $it.", Severity.HINT)
        }.getOrElse { emptyList() }

    /**
     * Return a meaningful identifier for this vulnerability that can be used in reports. Obtain this identifier from
     * the defined aliases if there are any. The data model of VulnerableCode supports multiple aliases while ORT's
     * [Vulnerability] has just one identifier. To resolve this discrepancy, prefer CVEs over other identifiers. If
     * there are no aliases referencing CVEs, use an arbitrary alias, assuming that every alias is preferable over
     * the provider-specific ID of VulnerableCode. Only if no aliases are defined, use the latter as fallback. Note
     * that it should still be possible via the references to find mentions of aliases that have been dropped.
     */
    private fun VulnerableCodeService.Vulnerability.preferredCommonId(): String {
        if (aliases.isEmpty()) return vulnerabilityId

        return aliases.firstOrNull { it.startsWith("cve", ignoreCase = true) } ?: aliases.first()
    }
}

private val BACKSLASH_ESCAPE_REGEX = Regex("\\\\\\\\(.)")

internal fun String.fixupUrlEscaping(): String =
    replace(BACKSLASH_ESCAPE_REGEX) {
        it.groupValues[1].percentEncode()
    }
