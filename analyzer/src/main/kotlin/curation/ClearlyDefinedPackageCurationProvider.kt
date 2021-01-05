/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.analyzer.curation

import java.io.IOException

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
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.toSpdx
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace

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
 * A provider for curated package meta-data from the [ClearlyDefined](https://clearlydefined.io/) service.
 */
class ClearlyDefinedPackageCurationProvider(server: Server = Server.PRODUCTION) : PackageCurationProvider {
    private val service = ClearlyDefinedService.create(server, OkHttpClientHelper.buildClient())

    override fun getCurationsFor(pkgId: Identifier): List<PackageCuration> {
        val (type, provider) = pkgId.toClearlyDefinedTypeAndProvider() ?: return emptyList()
        val namespace = pkgId.namespace.takeUnless { it.isEmpty() } ?: "-"
        val curationCall = service.getCuration(type, provider, namespace, pkgId.name, pkgId.version)

        val response = try {
            curationCall.execute()
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Getting curations for '${pkgId.toCoordinates()}' failed with: ${e.collectMessagesAsString()}" }

            null
        }

        val curation = response?.body() ?: return emptyList()

        val declaredLicenseParsed = curation.licensed?.declared?.let { declaredLicense ->
            // Only take curations of good quality (i.e. those not using deprecated identifiers) and in particular none
            // that contain "OTHER" as a license, also see https://github.com/clearlydefined/curated-data/issues/7836.
            runCatching { declaredLicense.toSpdx(SpdxExpression.Strictness.ALLOW_CURRENT) }.getOrNull()?.toString()
        }

        val sourceLocation = curation.described?.sourceLocation.toArtifactOrVcs()

        val pkgCuration = PackageCuration(
            id = pkgId,
            data = PackageCurationData(
                declaredLicenses = declaredLicenseParsed?.let { sortedSetOf(it) },
                homepageUrl = curation.described?.projectWebsite?.toString(),
                sourceArtifact = sourceLocation as? RemoteArtifact,
                vcs = sourceLocation as? VcsInfoCurationData,
                comment = "Provided by ClearlyDefined."
            )
        )

        return listOf(pkgCuration)
    }
}
