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

package org.ossreviewtoolkit.plugins.packagecurationproviders.opensourceinsights

import com.squareup.wire.GrpcException

import deps_dev.v3.System as PackageSystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.opensourceinsights.OpenSourceInsightsService
import org.ossreviewtoolkit.clients.opensourceinsights.createInsightsClient
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.utils.ort.okHttpClient
import org.ossreviewtoolkit.utils.ort.runBlocking

/**
 * A provider for curated package metadata from the [Open Source Insights](https://deps.dev/) service. Currently only
 * supports curations for the [publishedAt][PackageCurationData.publishedAt] property.
 */
@OrtPlugin(
    displayName = "Open Source Insights",
    summary = "Provides package curation data from the Open Source Insights service.",
    factory = PackageCurationProviderFactory::class
)
class OpenSourceInsightsCurationProvider(
    override val descriptor: PluginDescriptor = OpenSourceInsightsCurationProviderFactory.descriptor
) : PackageCurationProvider {
    private val service by lazy {
        OpenSourceInsightsService(createInsightsClient(httpClient = okHttpClient))
    }

    override fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration> {
        val versionInfo = runBlocking(Dispatchers.IO.limitedParallelism(20)) {
            packages.map { pkg ->
                async {
                    val (system, name, version) = pkg.id.toInsights()

                    runCatching {
                        service.getVersion(system, name, version)
                    }.onFailure {
                        if (!(it is GrpcException && it.grpcStatus.name == "NOT_FOUND")) {
                            logger.error { "Failed to get version information for '${pkg.id.toCoordinates()}': $it" }
                        }
                    }.getOrNull()?.let {
                        pkg.id to it
                    }
                }
            }.awaitAll().filterNotNull()
        }

        return versionInfo.mapTo(mutableSetOf()) { (id, versionInfo) ->
            PackageCuration(
                id = id,
                data = PackageCurationData(
                    publishedAt = versionInfo.published_at
                )
            )
        }
    }
}

private fun Identifier.toInsights(): Triple<PackageSystem, String, String> {
    val (system, separator) = when (type.lowercase()) {
        "npm" -> PackageSystem.NPM to "/"
        "crate" -> PackageSystem.CARGO to ""
        "gem" -> PackageSystem.RUBYGEMS to ""
        "go" -> PackageSystem.GO to ""
        "maven" -> PackageSystem.MAVEN to ":"
        "nuget" -> PackageSystem.NUGET to ""
        "pypi" -> PackageSystem.PYPI to ""
        else -> PackageSystem.SYSTEM_UNSPECIFIED to ""
    }

    // Insights uses system-specific separators as part of the name. No encoding should be used with the gRPC API.
    val namespaceAndName = listOfNotNull(namespace.takeUnless { it.isEmpty() }, name).joinToString(separator)

    return Triple(system, namespaceAndName, version)
}
