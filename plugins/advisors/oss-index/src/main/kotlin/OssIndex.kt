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

package org.ossreviewtoolkit.plugins.advisors.ossindex

import java.net.URI
import java.time.Instant

import kotlin.coroutines.cancellation.CancellationException

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.clients.ossindex.OssIndexService
import org.ossreviewtoolkit.clients.ossindex.OssIndexService.ComponentReport
import org.ossreviewtoolkit.clients.ossindex.OssIndexService.ComponentReportRequest
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.vulnerabilities.Cvss2Rating
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.model.vulnerabilities.VulnerabilityReference
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

/**
 * The number of packages to request from Sonatype OSS Index in one request.
 */
private const val BULK_REQUEST_SIZE = 128

/**
 * A wrapper for Sonatype's [OSS Index](https://ossindex.sonatype.org/) security vulnerability data.
 */
@OrtPlugin(
    id = "OSSIndex",
    displayName = "OSS Index",
    description = "An advisor that uses Sonatype's OSS Index to determine vulnerabilities in dependencies.",
    factory = AdviceProviderFactory::class
)
class OssIndex(
    override val descriptor: PluginDescriptor = OssIndexFactory.descriptor,
    config: OssIndexConfiguration
) : AdviceProvider {
    override val details = AdvisorDetails(descriptor.id, enumSetOf(AdvisorCapability.VULNERABILITIES))

    private val service by lazy {
        OssIndexService.create(
            config.serverUrl,
            config.username?.value,
            config.password?.value,
            OkHttpClientHelper.buildClient()
        )
    }

    private val getComponentReport by lazy {
        val hasCredentials = config.username != null && config.password != null
        if (hasCredentials) service::getAuthorizedComponentReport else service::getComponentReport
    }

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()

        val purls = packages.mapNotNull { pkg -> pkg.purl.ifEmpty { null } }
        val chunks = purls.chunked(BULK_REQUEST_SIZE)

        val componentReports = mutableMapOf<String, ComponentReport>()
        val issues = mutableListOf<Issue>()

        chunks.forEachIndexed { index, chunk ->
            logger.debug { "Getting report for ${chunk.size} components (chunk ${index + 1} of ${chunks.size})." }

            runCatching {
                val results = getComponentReport(ComponentReportRequest(chunk)).associateBy {
                    it.coordinates
                }

                componentReports += results.filterValues { it.vulnerabilities.isNotEmpty() }
            }.onFailure {
                if (it is CancellationException) currentCoroutineContext().ensureActive()

                // Create dummy reports for all components in the chunk as the current data model does not allow to
                // return issues that are not associated to any package.
                componentReports += chunk.associateWith { purl ->
                    ComponentReport(purl, reference = "", vulnerabilities = emptyList())
                }

                issues += Issue(source = descriptor.displayName, message = it.collectMessages())
            }
        }

        val endTime = Instant.now()

        return packages.mapNotNullTo(mutableListOf()) { pkg ->
            componentReports[pkg.purl]?.let { report ->
                pkg to AdvisorResult(
                    details,
                    AdvisorSummary(startTime, endTime, issues),
                    vulnerabilities = report.vulnerabilities.map { it.toVulnerability() }
                )
            }
        }.toMap()
    }

    /**
     * Construct an [ORT Vulnerability][Vulnerability] from an [OssIndexService Vulnerability]
     * [OssIndexService.Vulnerability].
     */
    private fun OssIndexService.Vulnerability.toVulnerability(): Vulnerability {
        // Only CVSS version 2 vectors do not contain the "CVSS:" label and version prefix.
        val scoringSystem = cvssVector?.substringBefore('/', Cvss2Rating.PREFIXES.first())

        val severity = VulnerabilityReference.getQualitativeRating(scoringSystem, cvssScore)?.name
        val reference = VulnerabilityReference(URI(reference), scoringSystem, severity, cvssScore, cvssVector)

        val references = mutableListOf(reference)
        externalReferences?.mapTo(references) { reference.copy(url = URI(it)) }
        return Vulnerability(
            id = cve ?: displayName ?: title,
            summary = title,
            description = description,
            references = references
        )
    }
}
