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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

import org.ossreviewtoolkit.clients.crossd.getAverageValues
import org.ossreviewtoolkit.clients.crossd.getMetrics
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.HealthMetric
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProvider
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProviderFactory
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.ort.okHttpClient

/**
 * An [AdviceProvider] implementation that obtains health metrics from a [CrOSSD][https://health.crossd.tech/doc]
 * instance.
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
            average?.let { metric.averageValue = average }
        }

        val startTime = Instant.now()

        // Find project owner and name from repository URL.
        val packageIds = packages.associateWith { pkg ->
            val vcsHost = VcsHost.fromUrl(pkg.vcsProcessed.url)
            vcsHost?.let { vcsHost ->
                val projectOrg = vcsHost.getUserOrOrganization(pkg.vcsProcessed.url)
                val projectName = vcsHost.getProject(pkg.vcsProcessed.url)
                "$projectOrg/$projectName"
            }
        }

        // Report failed repository URL parsing.
        val issues = packageIds.filterValues { it == null }.mapValues { pkg ->
            Issue(
                source = descriptor.displayName,
                message = "The VCS URL '${pkg.key.vcsProcessed.url}' could not be mapped to a repository."
            )
        }

        // Query the actual metrics data.
        val metrics = withContext(Dispatchers.IO.limitedParallelism(20)) {
            packageIds.mapValues { entry ->
                async {
                    entry.value?.let { packageId ->
                        val metrics = client.getMetrics(packageId)
                        CROSSD_METRICS.mapNotNull { it.toHealthMetric(metrics) }
                    }.orEmpty()
                }
            }.mapValues { it.value.await() }
        }

        val endTime = Instant.now()

        return metrics.mapValues { (pkg, healthMetrics) ->
            val summary = AdvisorSummary(startTime, endTime, listOfNotNull(issues[pkg]))
            AdvisorResult(details, summary, emptyList(), healthMetrics)
        }
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
