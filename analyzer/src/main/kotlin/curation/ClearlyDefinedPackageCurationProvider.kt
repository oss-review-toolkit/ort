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
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.SourceLocation
import org.ossreviewtoolkit.clients.clearlydefined.ComponentType
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.toClearlyDefinedTypeAndProvider
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.showStackTrace
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

import retrofit2.HttpException

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

    override fun getCurationsFor(pkgId: Identifier): List<PackageCuration> {
        val (type, provider) = pkgId.toClearlyDefinedTypeAndProvider() ?: return emptyList()
        val namespace = pkgId.namespace.takeUnless { it.isEmpty() } ?: "-"

        val curation = runCatching {
            // TODO: Maybe make PackageCurationProvider.getCurationsFor() a suspend function; then all derived
            //       classes could deal with coroutines more easily.
            runBlocking(Dispatchers.IO) { service.getCuration(type, provider, namespace, pkgId.name, pkgId.version) }
        }.onFailure { e ->
            when (e) {
                is HttpException -> {
                    // An "HTTP_NOT_FOUND" is expected for non-existing curations, so only handle other codes as a
                    // failure.
                    if (e.code() != HttpURLConnection.HTTP_NOT_FOUND) {
                        e.showStackTrace()

                        log.warn {
                            val message = e.response()?.errorBody()?.string() ?: e.collectMessagesAsString()
                            "Getting curations for '${pkgId.toCoordinates()}' failed with code ${e.code()}: $message"
                        }
                    }
                }

                is JsonMappingException -> {
                    e.showStackTrace()

                    log.warn { "Deserializing the ClearlyDefined curation for '${pkgId.toCoordinates()}' failed." }
                }

                else -> {
                    e.showStackTrace()

                    log.warn {
                        "Querying ClearlyDefined curation for '${pkgId.toCoordinates()}' failed: " +
                                e.collectMessagesAsString()
                    }
                }
            }
        }.getOrNull() ?: return emptyList()

        val declaredLicenseParsed = curation.licensed?.declared?.let { declaredLicense ->
            // Only take curations of good quality (i.e. those not using deprecated identifiers) and in particular none
            // that contain "OTHER" as a license, also see https://github.com/clearlydefined/curated-data/issues/7836.
            runCatching { declaredLicense.toSpdx(SpdxExpression.Strictness.ALLOW_CURRENT) }.getOrNull()
        }

        val sourceLocation = curation.described?.sourceLocation.toArtifactOrVcs()

        val pkgCuration = PackageCuration(
            id = pkgId,
            data = PackageCurationData(
                concludedLicense = declaredLicenseParsed,
                homepageUrl = curation.described?.projectWebsite?.toString(),
                sourceArtifact = sourceLocation as? RemoteArtifact,
                vcs = sourceLocation as? VcsInfoCurationData,
                comment = "Provided by ClearlyDefined."
            )
        )

        return listOf(pkgCuration)
    }
}
