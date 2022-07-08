/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.analyzer.curation

import com.fasterxml.jackson.databind.JsonMappingException

import java.net.HttpURLConnection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

import okhttp3.OkHttpClient

import org.ossreviewtoolkit.analyzer.PackageCurationProvider
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.clients.clearlydefined.ComponentType
import org.ossreviewtoolkit.clients.clearlydefined.Coordinates
import org.ossreviewtoolkit.clients.clearlydefined.SourceLocation
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.toClearlyDefinedTypeAndProvider
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.showStackTrace
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

import retrofit2.HttpException

/**
 * The number of elements to request at once in a bulk request. This value was chosen more or less randomly to keep the
 * size of responses reasonably small.
 */
private const val BULK_REQUEST_SIZE = 100

/**
 * Map a ClearlyDefined [SourceLocation] to either a [VcsInfoCurationData] or a [RemoteArtifact].
 */
fun SourceLocation?.toArtifactOrVcs(): Any? =
    this?.let { sourceLocation ->
        when (sourceLocation.type) {
            ComponentType.GIT -> {
                VcsInfoCurationData(
                    type = VcsType.GIT,
                    url = sourceLocation.url,
                    revision = sourceLocation.revision,
                    path = sourceLocation.path
                )
            }

            else -> {
                val url = sourceLocation.url ?: run {
                    when (sourceLocation.provider) {
                        // TODO: Implement provider-specific mapping of coordinates to URLs.
                        else -> ""
                    }
                }

                RemoteArtifact(
                    url = url,
                    hash = Hash.NONE
                )
            }
        }
    }

/**
 * A provider for curated package metadata from the [ClearlyDefined](https://clearlydefined.io/) service.
 */
class ClearlyDefinedPackageCurationProvider(
    serverUrl: String,
    client: OkHttpClient? = null
) : PackageCurationProvider {
    constructor(server: Server = Server.PRODUCTION) : this(server.url)

    private val service by lazy { ClearlyDefinedService.create(serverUrl, client ?: OkHttpClientHelper.buildClient()) }

    override fun getCurationsFor(pkgIds: Collection<Identifier>): Map<Identifier, List<PackageCuration>> {
        val coordinatesToIds = pkgIds.mapNotNull { pkgId ->
            pkgId.toClearlyDefinedTypeAndProvider()?.let { (type, provider) ->
                val namespace = pkgId.namespace.takeUnless { it.isEmpty() } ?: "-"
                Coordinates(type, provider, namespace, pkgId.name, pkgId.version) to pkgId
            }
        }.toMap()

        val contributedCurations = runCatching {
            buildMap {
                coordinatesToIds.keys.chunked(BULK_REQUEST_SIZE).forEach { coordinates ->
                    putAll(runBlocking(Dispatchers.IO) { service.getCurations(coordinates) })
                }
            }
        }.onFailure { e ->
            when (e) {
                is HttpException -> {
                    // An "HTTP_NOT_FOUND" is expected for non-existing curations, so only handle other codes as a
                    // failure.
                    if (e.code() != HttpURLConnection.HTTP_NOT_FOUND) {
                        e.showStackTrace()

                        log.warn {
                            val message = e.response()?.errorBody()?.string() ?: e.collectMessages()
                            "Getting curations failed with code ${e.code()}: $message"
                        }
                    }
                }

                is JsonMappingException -> {
                    e.showStackTrace()
                    log.warn { "Deserializing the curations failed: ${e.collectMessages()}" }
                }

                else -> {
                    e.showStackTrace()
                    log.warn { "Querying curations failed: ${e.collectMessages()}" }
                }
            }
        }.getOrNull() ?: return emptyMap()

        val curations = mutableMapOf<Identifier, MutableList<PackageCuration>>()

        contributedCurations.forEach { (_, contributions) ->
            contributions.curations.forEach inner@{ (coordinates, curation) ->
                val pkgId = coordinatesToIds[coordinates] ?: return@inner

                val declaredLicenseParsed = curation.licensed?.declared?.let { declaredLicense ->
                    // Only take curations of good quality (i.e. those not using deprecated identifiers) and in
                    // particular none that contain "OTHER" as a license, also see
                    // https://github.com/clearlydefined/curated-data/issues/7836.
                    runCatching { declaredLicense.toSpdx(SpdxExpression.Strictness.ALLOW_CURRENT) }.getOrNull()
                }

                val sourceLocation = curation.described?.sourceLocation.toArtifactOrVcs()

                val data = PackageCurationData(
                    concludedLicense = declaredLicenseParsed,
                    homepageUrl = curation.described?.projectWebsite?.toString(),
                    sourceArtifact = sourceLocation as? RemoteArtifact,
                    vcs = sourceLocation as? VcsInfoCurationData
                )

                if (data != PackageCurationData()) {
                    curations.getOrPut(pkgId) { mutableListOf() } += PackageCuration(
                        id = pkgId,
                        data = data.copy(comment = "Provided by ClearlyDefined.")
                    )
                }
            }
        }

        return curations.mapValues { (_, curations) -> curations.distinct() }
    }
}
