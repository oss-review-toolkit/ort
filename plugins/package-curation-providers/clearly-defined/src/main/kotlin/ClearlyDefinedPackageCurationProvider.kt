/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.clearlydefined

import com.fasterxml.jackson.databind.JsonMappingException

import java.net.HttpURLConnection

import okhttp3.OkHttpClient

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.clients.clearlydefined.ComponentType
import org.ossreviewtoolkit.clients.clearlydefined.Coordinates
import org.ossreviewtoolkit.clients.clearlydefined.SourceLocation
import org.ossreviewtoolkit.clients.clearlydefined.getCurationsChunked
import org.ossreviewtoolkit.clients.clearlydefined.getDefinitionsChunked
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.PackageCurationProvider
import org.ossreviewtoolkit.model.utils.toClearlyDefinedCoordinates
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.showStackTrace
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

import retrofit2.HttpException

class ClearlyDefinedPackageCurationProviderConfig(
    /**
     * The URL of the ClearlyDefined server to use. If null, uses the [production server][Server.PRODUCTION.apiUrl].
     */
    val serverUrl: String,

    /**
     * The minimum total score for a curation to be accepted. Must lie within 0 to 100.
     */
    val minTotalLicenseScore: Int
)

class ClearlyDefinedPackageCurationProviderFactory :
    PackageCurationProviderFactory<ClearlyDefinedPackageCurationProviderConfig> {
    override val type = "ClearlyDefined"

    override fun create(config: ClearlyDefinedPackageCurationProviderConfig) =
        ClearlyDefinedPackageCurationProvider(config)

    override fun parseConfig(config: Map<String, String>) =
        ClearlyDefinedPackageCurationProviderConfig(
            serverUrl = config["serverUrl"] ?: Server.PRODUCTION.apiUrl,
            minTotalLicenseScore = config["minTotalLicenseScore"]?.toInt() ?: 0
        )
}

/**
 * A provider for curated package metadata from the [ClearlyDefined](https://clearlydefined.io/) service.
 */
class ClearlyDefinedPackageCurationProvider(
    private val config: ClearlyDefinedPackageCurationProviderConfig,
    client: OkHttpClient? = null
) : PackageCurationProvider {
    companion object : Logging

    constructor(serverUrl: String, client: OkHttpClient? = null) : this(
        ClearlyDefinedPackageCurationProviderConfig(serverUrl, minTotalLicenseScore = 0), client
    )

    constructor(server: Server = Server.PRODUCTION) : this(server.apiUrl)

    private val service by lazy {
        ClearlyDefinedService.create(config.serverUrl, client ?: OkHttpClientHelper.buildClient())
    }

    override fun getCurationsFor(packages: Collection<Package>): Set<PackageCuration> {
        val coordinatesToIds = mutableMapOf<Coordinates, Identifier>()

        packages.forEach { pkg ->
            val coordinates = pkg.toClearlyDefinedCoordinates()
            if (coordinates != null) {
                coordinatesToIds[coordinates] = pkg.id
            } else {
                logger.warn { "Unable to create ClearlyDefined coordinates for $pkg." }
            }
        }

        val curations = runCatching {
            service.getCurationsChunked(coordinatesToIds.keys)
        }.onFailure { e ->
            when (e) {
                is HttpException -> {
                    // An "HTTP_NOT_FOUND" is expected for non-existing curations, so only handle other codes as a
                    // failure.
                    if (e.code() != HttpURLConnection.HTTP_NOT_FOUND) {
                        e.showStackTrace()

                        logger.warn {
                            val message = e.response()?.errorBody()?.string() ?: e.collectMessages()
                            "Getting curations failed with code ${e.code()}: $message"
                        }
                    }
                }

                is JsonMappingException -> {
                    e.showStackTrace()
                    logger.warn { "Deserializing the curations failed: ${e.collectMessages()}" }
                }

                else -> {
                    e.showStackTrace()
                    logger.warn { "Querying curations failed: ${e.collectMessages()}" }
                }
            }
        }.getOrElse {
            return emptySet()
        }

        val filteredCurations = if (config.minTotalLicenseScore > 0) {
            val definitions = service.getDefinitionsChunked(curations.keys)

            curations.filterKeys { coordinates ->
                val score = definitions[coordinates]?.licensed?.score?.total
                score == null || score >= config.minTotalLicenseScore
            }
        } else {
            curations
        }

        val pkgCurations = mutableSetOf<PackageCuration>()

        filteredCurations.forEach inner@{ (coordinates, curation) ->
            val pkgId = coordinatesToIds[coordinates] ?: return@inner

            val declaredLicenseParsed = curation.licensed?.declared?.let { declaredLicense ->
                // Only take curations of good quality (i.e. those not using deprecated identifiers) and in
                // particular none that contain "OTHER" as a license, also see
                // https://github.com/clearlydefined/curated-data/issues/7836.
                runCatching { declaredLicense.toSpdx(SpdxExpression.Strictness.ALLOW_CURRENT) }.getOrNull()
            }

            val sourceLocation = curation.described?.sourceLocation?.toArtifactOrVcs()

            val data = PackageCurationData(
                concludedLicense = declaredLicenseParsed,
                homepageUrl = curation.described?.projectWebsite?.toString(),
                sourceArtifact = sourceLocation as? RemoteArtifact,
                vcs = sourceLocation as? VcsInfoCurationData
            )

            if (data != PackageCurationData()) {
                pkgCurations += PackageCuration(
                    id = pkgId,
                    data = data.copy(comment = "Provided by ClearlyDefined.")
                )
            }
        }

        return pkgCurations
    }
}

/**
 * Map a ClearlyDefined [SourceLocation] to either a [VcsInfoCurationData] or a [RemoteArtifact].
 */
private fun SourceLocation.toArtifactOrVcs(): Any =
    when (type) {
        ComponentType.GIT -> VcsInfoCurationData(
            type = VcsType.GIT,
            url = url,
            revision = revision,
            path = path
        )

        else -> RemoteArtifact(
            // TODO: Implement provider-specific mapping of coordinates to URLs.
            url = url.orEmpty(),
            hash = Hash.NONE
        )
    }
