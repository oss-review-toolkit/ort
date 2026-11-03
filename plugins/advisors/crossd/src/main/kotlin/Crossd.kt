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

package org.ossreviewtoolkit.plugins.advisors.crossd

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

import java.time.Instant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.crossd.getAverageValues
import org.ossreviewtoolkit.clients.crossd.getMetrics
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.HealthMetric
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProvider
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProviderFactory
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.okHttpClient

/**
 * An [AdviceProvider] implementation that obtains health metrics from a [CrOSSD](https://health.crossd.tech/doc)
 * instance. Note that this advisor currently
 * [only supports GitHub repositories](https://github.com/FH-CrOSSD/crossd#known-issues-and-limitations).
 */
@OrtPlugin(
    id = "Crossd",
    displayName = "CrOSSD",
    summary = "An advisor that uses a CrOSSD instance to determine health of dependencies.",
    factory = AdviceProviderFactory::class
)
class Crossd(
    override val descriptor: PluginDescriptor = CrossdFactory.descriptor,
    config: CrossdConfig
) : AdviceProvider {
    private val client = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }

        install(DefaultRequest) {
            url(config.serverUrl)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
            )
        }
    }

    override val details = AdvisorDetails(descriptor.id)

    private val thresholds = config.getThresholds()

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        // Update average values that are used for the value ratings.
        val averages = client.getAverageValues()
        CROSSD_METRICS.forEach { metric ->
            val average = averages[metric.name]
            average?.also { metric.averageValue = average }
        }

        val startTime = Instant.now()

        // Find project owner and name from repository URL. Note that the "project is currently only intended for use
        // with GitHub repositories", see https://github.com/FH-CrOSSD/crossd#known-issues-and-limitations.
        val packageIds = packages.mapNotNull { pkg ->
            val slug = when (val vcsHost = VcsHost.fromUrl(pkg.vcsProcessed.url)) {
                VcsHost.GITHUB -> {
                    val projectOrg = vcsHost.getUserOrOrganization(pkg.vcsProcessed.url)
                    val projectName = vcsHost.getProject(pkg.vcsProcessed.url)
                    "$projectOrg/$projectName"
                }

                else -> {
                    logger.debug { "The VCS URL '${pkg.vcsProcessed.url}' could not be mapped to a GitHub repository." }
                    null
                }
            }

            slug?.let { pkg to it }
        }.toMap()

        // Query the actual metrics data.
        val metrics = withContext(Dispatchers.IO.limitedParallelism(20)) {
            packageIds.map { (pkg, slug) ->
                async {
                    pkg to runCatching {
                        val metrics = client.getMetrics(slug)
                        CROSSD_METRICS.mapNotNull { it.toHealthMetric(metrics) }
                    }
                }
            }.awaitAll().toMap()
        }

        val endTime = Instant.now()

        return metrics.mapNotNull { (pkg, result) ->
            val issues = mutableListOf<Issue>()

            val healthMetrics = result.getOrElse {
                issues += createAndLogIssue(
                    "Unable to get metrics for '${pkg.id.toCoordinates()}': ${it.collectMessages()}"
                )

                emptyList()
            }

            // If there is neither metrics nor issues, there is no reason to create an advisor result for this package.
            if (healthMetrics.isEmpty() && issues.isEmpty()) return@mapNotNull null

            val summary = AdvisorSummary(startTime, endTime, issues)

            pkg to AdvisorResult(details, summary, healthMetrics = healthMetrics)
        }.toMap()
    }

    private fun CrossdMetric.toHealthMetric(metrics: JsonObject): HealthMetric? {
        val value = valueGetter(metrics)
        return value?.let {
            HealthMetric(
                name = displayName,
                value = value,
                criticality = getCriticality(value, thresholds),
                documentation = descriptionShort,
                documentationLink = documentationUrl,
                details = emptyList()
            )
        }
    }
}
