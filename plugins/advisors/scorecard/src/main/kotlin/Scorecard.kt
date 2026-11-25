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
import java.time.LocalDate
import java.time.ZoneOffset

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.clients.scorecard.ScorecardCheck
import org.ossreviewtoolkit.clients.scorecard.ScorecardResult
import org.ossreviewtoolkit.clients.scorecard.client.getResult
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Criticality
import org.ossreviewtoolkit.model.HealthMetric
import org.ossreviewtoolkit.model.HealthSummary
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProvider
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProviderFactory
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.ort.okHttpClient

/** The documentation about how Scorecard calculates its scores. */
private const val SCORING_URL = "https://github.com/ossf/scorecard#aggregate-score"

/** The lowest score Scorecard assigns to a check or a repository, see [SCORING_URL]. */
private const val MIN_SCORE = 0.0

/** The highest score Scorecard assigns to a check or a repository, see [SCORING_URL]. */
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

        val endTime = Instant.now()

        return responses.mapValues { (_, scorecardResult) ->
            AdvisorResult(
                advisor = details,
                summary = AdvisorSummary(startTime, endTime, issues),
                healthMetrics = scorecardResult?.checks?.mapNotNull { it.toHealthMetric() }.orEmpty(),
                healthSummary = scorecardResult?.toHealthSummary()
            )
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
        criticality = computedScore?.let { determineValueCriticality(it.toDouble()) },
        reason = reason,
        details = details
    )
}

internal fun ScorecardResult.toHealthSummary(): HealthSummary? {
    val summary = HealthSummary(
        overallScore = score?.let { toOverallScore(it) },
        analyzedAt = date?.let { parseDate(it) },
        analyzedRevision = repo?.commit,
        toolVersion = scorecard?.version
    )

    return summary.takeUnless { it == HealthSummary() }
}

private fun toOverallScore(score: Double): HealthMetric {
    // Treat a negative score like the score of a check that Scorecard could not compute.
    val value = score.takeIf { it >= 0 }

    return HealthMetric(
        name = "Aggregate-Score",
        documentation = "The average of the check scores, weighted by the risk of each check.",
        documentationLink = URI(SCORING_URL),
        minValue = MIN_SCORE,
        maxValue = MAX_SCORE,
        higherIsBetter = true,
        value = value,
        criticality = value?.let { determineValueCriticality(it) }
    )
}

/**
 * Parse the [date] of a Scorecard analysis. Scorecard reports it either as a full timestamp or only as a date formatted
 * as YYYY-MM-DD, which is taken as the start of that day in UTC. Return null if the [date] cannot be parsed.
 */
private fun parseDate(date: String): Instant? =
    runCatching { Instant.parse(date) }
        .recoverCatching { LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant() }
        .getOrNull()

internal fun determineValueCriticality(value: Double): Criticality =
    when {
        value < 3 -> Criticality.CRITICAL
        value < 5 -> Criticality.HIGH
        value < 8 -> Criticality.MEDIUM
        else -> Criticality.LOW
    }
