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

import java.io.IOException
import java.net.URI
import java.time.Instant

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.advisor.AbstractAdviceProviderFactory
import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.clients.ossindex.OssIndexService
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.OssIndexConfiguration
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper

import retrofit2.HttpException

/**
 * The number of packages to request from Sonatype OSS Index in one request.
 */
private const val BULK_REQUEST_SIZE = 128

/**
 * A wrapper for Sonatype's [OSS Index](https://ossindex.sonatype.org/) security vulnerability data.
 */
class OssIndex(name: String, config: OssIndexConfiguration) : AdviceProvider(name) {
    companion object : Logging

    class Factory : AbstractAdviceProviderFactory<OssIndex>("OssIndex") {
        override fun create(config: AdvisorConfiguration) =
            OssIndex(type, config.forProvider { ossIndex ?: OssIndexConfiguration() })
    }

    override val details = AdvisorDetails(providerName, enumSetOf(AdvisorCapability.VULNERABILITIES))

    private val service by lazy {
        OssIndexService.create(
            url = config.serverUrl,
            client = OkHttpClientHelper.buildClient()
        )
    }

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, List<AdvisorResult>> {
        val startTime = Instant.now()

        val purls = packages.mapNotNull { pkg -> pkg.purl.takeUnless { it.isEmpty() } }

        return try {
            val componentReports = mutableMapOf<String, OssIndexService.ComponentReport>()

            val chunks = purls.chunked(BULK_REQUEST_SIZE)
            chunks.forEachIndexed { index, chunkOfPurls ->
                logger.debug {
                    "Getting report for ${chunkOfPurls.size} components (chunk ${index + 1} of ${chunks.size})."
                }

                val requestResults = getComponentReport(service, chunkOfPurls).associateBy {
                    it.coordinates
                }

                componentReports += requestResults.filterValues { it.vulnerabilities.isNotEmpty() }
            }

            val endTime = Instant.now()

            packages.mapNotNullTo(mutableListOf()) { pkg ->
                componentReports[pkg.id.toPurl()]?.let { report ->
                    pkg to listOf(
                        AdvisorResult(
                            details,
                            AdvisorSummary(startTime, endTime),
                            vulnerabilities = report.vulnerabilities.map { it.toVulnerability() }
                        )
                    )
                }
            }.toMap()
        } catch (e: IOException) {
            createFailedResults(startTime, packages, e)
        }
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

    /**
     * Invoke the [OSS Index service][service] to request detail information for the given [purls]. Catch HTTP
     * exceptions thrown by the service and re-throw them as [IOException].
     */
    private suspend fun getComponentReport(
        service: OssIndexService,
        purls: List<String>
    ): List<OssIndexService.ComponentReport> =
        try {
            service.getComponentReport(OssIndexService.ComponentReportRequest(purls))
        } catch (e: HttpException) {
            throw IOException(e)
        }
}
