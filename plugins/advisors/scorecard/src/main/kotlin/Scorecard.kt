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

package org.ossreviewtoolkit.plugins.advisors.scorecard

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

import java.net.URI
import java.time.Instant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.clients.scorecard.ScorecardCheck
import org.ossreviewtoolkit.clients.scorecard.client.getResult
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Criticality
import org.ossreviewtoolkit.model.HealthMetric
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProvider
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProviderFactory
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.ort.okHttpClient

/** The lowest score Scorecard assigns to a check, see https://github.com/ossf/scorecard#aggregate-score. */
private const val MIN_SCORE = 0.0

/** The highest score Scorecard assigns to a check, see https://github.com/ossf/scorecard#aggregate-score. */
private const val MAX_SCORE = 10.0

/**
 * An [AdviceProvider] implementation that obtains health metrics from a [Scorecard][https://github.com/ossf/scorecard]
 * instance.
 */
@OrtPlugin(
    displayName = "Scorecard",
    summary = "An advisor that uses a Scorecard instance to determine health of dependencies.",
    factory = AdviceProviderFactory::class
)
class Scorecard(
    override val descriptor: PluginDescriptor = ScorecardFactory.descriptor,
    config: ScorecardConfig
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

    override suspend fun retrievePackageFindings(packages: Set<Package>): Map<Package, AdvisorResult> {
        val startTime = Instant.now()
        val issues = mutableListOf<Issue>()

        val packageInfo = packages.associateWith { pkg ->
            val vcsHost = VcsHost.fromUrl(pkg.vcsProcessed.url)

            vcsHost?.let { host ->
                val hostname = host.hostname
                val projectOrg = host.getUserOrOrganization(pkg.vcsProcessed.url)
                val projectName = host.getProject(pkg.vcsProcessed.url)

                if (projectOrg != null && projectName != null) {
                    listOf(hostname, projectOrg, projectName)
                } else {
                    null
                }
            }
        }

        val responses = withContext(Dispatchers.IO.limitedParallelism(20)) {
            packageInfo.mapValues { (_, repoData) ->
                async {
                    repoData?.let {
                        val (platform, org, repo) = it
                        client.getResult(platform, org, repo)
                    }
                }
            }
        }.mapValues { it.value.await() }

        val healthMetricList = responses.map { (pkg, scorecardResult) ->
            val healthData = scorecardResult?.checks?.mapNotNull { it.toHealthMetric() }.orEmpty()
            pkg to healthData
        }

        val endTime = Instant.now()

        return healthMetricList.associate { (pkg, healthData) ->
            pkg to AdvisorResult(details, AdvisorSummary(startTime, endTime, issues), emptyList(), healthData)
        }
    }
}

internal fun ScorecardCheck.toHealthMetric(): HealthMetric? {
    // Scorecard reports a score of -1 for a check it could not compute, and gives the reason.
    val computedScore = score?.takeIf { it >= 0 }

    return HealthMetric(
        name = name ?: return null,
        documentation = documentation?.short,
        documentationLink = documentation?.url?.let { URI(it) },
        minValue = MIN_SCORE,
        maxValue = MAX_SCORE,
        higherIsBetter = true,
        value = computedScore?.toDouble(),
        criticality = computedScore?.let { determineValueCriticality(it) },
        reason = reason,
        details = details
    )
}

internal fun determineValueCriticality(value: Int): Criticality =
    when {
        value < 3 -> Criticality.CRITICAL
        value < 5 -> Criticality.HIGH
        value < 8 -> Criticality.MEDIUM
        else -> Criticality.LOW
    }
