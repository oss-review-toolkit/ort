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

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.advisor.AbstractAdviceProviderFactory
import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.clients.ossindex.OssIndexService
import org.ossreviewtoolkit.clients.ossindex.OssIndexService.ComponentReport
import org.ossreviewtoolkit.clients.ossindex.OssIndexService.ComponentReportRequest
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.OssIndexConfiguration
import org.ossreviewtoolkit.model.utils.toPurl
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
class OssIndex(name: String, config: OssIndexConfiguration) : AdviceProvider(name) {
    private companion object : Logging

    class Factory : AbstractAdviceProviderFactory<OssIndex>("OssIndex") {
        override fun create(config: AdvisorConfiguration) =
            OssIndex(type, config.forProvider { ossIndex ?: OssIndexConfiguration() })
    }

    override val details = AdvisorDetails(providerName, enumSetOf(AdvisorCapability.VULNERABILITIES))

    private val service by lazy {
        OssIndexService.create(
            config.serverUrl,
            config.username,
            config.password,
            OkHttpClientHelper.buildClient()
        )
    }

    private val getComponentReport by lazy {
        val hasCredentials = config.username != null && config.password != null
        if (hasCredentials) service::getAuthorizedComponentReport else service::getComponentReport
    }

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()

        val purls = packages.mapNotNull { pkg -> pkg.purl.takeUnless { it.isEmpty() } }
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
                // Create dummy reports for all components in the chunk as the current data model does not allow to
                // return issues that are not associated to any package.
                componentReports += chunk.associateWith { purl ->
                    ComponentReport(purl, reference = "", vulnerabilities = emptyList())
                }

                issues += Issue(source = providerName, message = it.collectMessages())
            }
        }

        val endTime = Instant.now()

        return packages.mapNotNullTo(mutableListOf()) { pkg ->
            componentReports[pkg.id.toPurl()]?.let { report ->
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
        val reference = VulnerabilityReference(
            url = URI(reference),
            scoringSystem = cvssVector?.substringBefore('/'),
            severity = cvssScore.toString()
        )

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
