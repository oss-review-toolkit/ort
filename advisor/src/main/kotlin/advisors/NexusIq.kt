/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

import java.io.IOException
import java.time.Instant
import java.util.concurrent.Executors

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.advisor.AbstractAdvisorFactory
import org.ossreviewtoolkit.advisor.Advisor
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.BasicAuthConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.PurlType
import org.ossreviewtoolkit.model.utils.getPurlType
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.nexusiq.NexusIqService
import org.ossreviewtoolkit.utils.NamedThreadFactory
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace

import retrofit2.Call

/**
 * The number of packages to request from Nexus IQ in one request.
 */
private const val REQUEST_CHUNK_SIZE = 100

/**
 * A wrapper for [Nexus IQ Server](https://help.sonatype.com/iqserver) security vulnerability data.
 */
class NexusIq(
    name: String,
    config: AdvisorConfiguration
) : Advisor(name, config) {
    class Factory : AbstractAdvisorFactory<NexusIq>("NexusIQ") {
        override fun create(config: AdvisorConfiguration) = NexusIq(advisorName, config)
    }

    private val nexusIqConfig = config as BasicAuthConfiguration

    override suspend fun retrievePackageVulnerabilities(packages: List<Package>): Map<Package, List<AdvisorResult>> {
        val service = NexusIqService.create(
            nexusIqConfig.serverUrl,
            nexusIqConfig.username,
            nexusIqConfig.password,
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

                NexusIqService.Component(packageUrl)
            }

            try {
                val componentDetails = mutableMapOf<String, NexusIqService.ComponentDetails>()

                components.chunked(REQUEST_CHUNK_SIZE).forEach { component ->
                    val serviceCall = withContext(advisorDispatcher) {
                        service.getComponentDetails(NexusIqService.ComponentsWrapper(component))
                    }

                    val requestResults = execute(serviceCall).componentDetails.associateBy {
                        it.component.packageUrl.substringBefore("?")
                    }

                    componentDetails += requestResults
                }

                val endTime = Instant.now()

                packages.mapNotNullTo(mutableListOf()) { pkg ->
                    componentDetails[pkg.id.toPurl()]?.takeUnless {
                        it.securityData.securityIssues.isEmpty()
                    }?.let { details ->
                        pkg to listOf(
                            AdvisorResult(
                                details.securityData.securityIssues.mapToVulnerabilities(),
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

    private fun Collection<NexusIqService.SecurityIssue>.mapToVulnerabilities() =
        map {
            Vulnerability(
                it.reference,
                it.severity,
                it.url
            )
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
