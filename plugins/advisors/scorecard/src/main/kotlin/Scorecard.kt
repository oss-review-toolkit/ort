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

import java.time.Instant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.clients.scorecard.ScorecardResult
import org.ossreviewtoolkit.clients.scorecard.client.getResult
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Criticality
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ProjectHealth
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProvider
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProviderFactory
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.ort.getVcsUrlParts
import org.ossreviewtoolkit.utils.ort.okHttpClient

/**
 * An [AdviceProvider] implementation that obtains project health metrics from a
 * [SCORECARD][https://github.com/ossf/scorecard] instance.
 */
@OrtPlugin(
    id = "Scorecard",
    displayName = "SCORECARD",
    summary = "An advisor that uses a SCORECARD instance to determine project health in dependencies.",
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

        val packageInfo = packages
            .associateWith { getVcsUrlParts(it.vcsProcessed.url) }

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

        val projectHealthList: List<Pair<Package, List<ProjectHealth>>> =
            responses.map { (pkg, scorecardResult) ->
                val healthData = scorecardResult?.toProjectHealthList().orEmpty()
                pkg to healthData
            }

        val endTime = Instant.now()

        return projectHealthList.associate { (pkg, healthData) ->
            pkg to AdvisorResult(details, AdvisorSummary(startTime, endTime, issues), emptyList(), healthData)
        }
    }

    fun ScorecardResult.toProjectHealthList(): List<ProjectHealth> =
        this.checks
            .filter { metric -> metric.name != null && metric.score != null && metric.score != -1 }
            .map { metric ->
                ProjectHealth(
                    name = metric.name!!,
                    value = metric.score!!.toDouble(),
                    criticality = metric.score?.let { determineValueCriticality(it) },
                    reason = metric.reason,
                    details = metric.details,
                    documentation = metric.documentation?.short,
                    documentationLink = metric.documentation?.url,
                    source = descriptor.id
                )
            }

    fun determineValueCriticality(value: Int): Criticality =
        when {
            value < 3 -> Criticality.Critical
            value < 5 -> Criticality.High
            value < 8 -> Criticality.Medium
            else -> Criticality.Low
        }
}
