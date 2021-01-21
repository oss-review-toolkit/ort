/*
 * Copyright (C) 2021 Sonatype
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import kotlinx.coroutines.asCoroutineDispatcher
import java.io.IOException
import java.time.Instant
import java.util.concurrent.Executors

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.advisor.AbstractAdvisorFactory
import org.ossreviewtoolkit.advisor.Advisor
import org.ossreviewtoolkit.clients.ossindex.OssIndexService
import org.ossreviewtoolkit.model.*
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.NexusIqConfiguration
import org.ossreviewtoolkit.model.utils.PurlType
import org.ossreviewtoolkit.model.utils.getPurlType
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.*

import retrofit2.Call
import java.net.URL

/**
 * The number of packages to request from Sonatype OSS Index in one request.
 */
private const val REQUEST_CHUNK_SIZE = 128

/**
 * A wrapper for [Sonatype OSS Index](https://ossindex.sonatype.org/) security vulnerability data.
 */
class OssIndex(
    name: String,
    config: AdvisorConfiguration
) : Advisor(name, config) {
    class Factory : AbstractAdvisorFactory<OssIndex>("SonatypeOssIndex") {
        override fun create(config: AdvisorConfiguration) = OssIndex(advisorName, config)
    }

    private val ossIndexConfig = config as NexusIqConfiguration

    override suspend fun retrievePackageVulnerabilities(packages: List<Package>): Map<Package, List<AdvisorResult>> {
        val service = OssIndexService.create(
            ossIndexConfig.serverUrl,
            ossIndexConfig.username,
            ossIndexConfig.password,
            OkHttpClientHelper.buildClient()
        )

        val advisorDispatcher =
            Executors.newSingleThreadExecutor(NamedThreadFactory(advisorName)).asCoroutineDispatcher()

        return coroutineScope {
            val startTime = Instant.now()

            val components = packages.map { pkg ->
                val packageUrl = buildString {
                    append(pkg.purl)

                    when (pkg.id.getPurlType()) {
                        PurlType.MAVEN.toString() -> append("?type=jar")
                        PurlType.PYPI.toString() -> append("?extension=tar.gz")
                    }
                }

                packageUrl
            }

            try {
                val componentDetails = mutableMapOf<String, OssIndexService.Component>()

                components.chunked(REQUEST_CHUNK_SIZE).forEach { component ->
                    val serviceCall = withContext(advisorDispatcher) {
                        service.getComponentReport(OssIndexService.ComponentsRequest(component))
                    }

                    val requestResults = execute(serviceCall).associateBy {
                        it.coordinates
                    }

                    componentDetails += requestResults
                }

                val endTime = Instant.now()

                packages.mapNotNullTo(mutableListOf()) { pkg ->
                    componentDetails[pkg.id.toPurl()]?.takeUnless {
                        it.vulnerabilities.isEmpty()
                    }?.let { details ->
                        pkg to listOf(
                            AdvisorResult(
                                details.vulnerabilities.map { it.toVulnerability() },
                                AdvisorDetails(advisorName),
                                AdvisorSummary(startTime, endTime)
                            )
                        )
                    }
                }.toMap()

            } catch (e: IOException) {
                e.showStackTrace()

                val now = Instant.now()
                packages.associateWith {
                    listOf(
                        AdvisorResult(
                            vulnerabilities = emptyList(),
                            advisor = AdvisorDetails(advisorName),
                            summary = AdvisorSummary(
                                startTime = now,
                                endTime = now,
                                issues = listOf(
                                    createAndLogIssue(
                                        source = advisorName,
                                        message = "Failed to retrieve security vulnerabilities from $advisorName: " +
                                                e.collectMessagesAsString()
                                    )
                                )
                            )
                        )
                    )
                }
            }
        }
    }

    private fun OssIndexService.Vulnerability.toVulnerability(): Vulnerability {
        val browseUrl = URL(reference)

        return Vulnerability(displayName, cvssScore, browseUrl)
    }

    /**
     * Execute an HTTP request specified by the given [call]. The response status is checked. If everything went
     * well, the marshalled body of the request is returned; otherwise, the function throws an exception.
     */
    private fun <T> execute(call: Call<T>): T {
        val request = "${call.request().method} on ${call.request().url}"
        log.debug { "Executing HTTP $request." }

        val response = call.execute()
        log.debug { "HTTP response is (status ${response.code()}): ${response.message()}" }

        val body = response.body()
        if (!response.isSuccessful || body == null) {
            throw IOException(
                "Failed HTTP $request with status ${response.code()} and error: ${response.errorBody()?.string()}."
            )
        }

        return body
    }
}
