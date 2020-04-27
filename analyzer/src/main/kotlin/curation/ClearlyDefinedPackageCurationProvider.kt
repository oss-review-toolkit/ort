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
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.ComponentType
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.Coordinates
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.Provider
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.SourceLocation
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace

/**
 * Map an [Identifier] to a ClearlyDefined [ComponentType] and [Provider]. Note that an Identifier's type in ORT
 * currently implies a default provider.
 */
fun Identifier.toClearlyDefinedTypeAndProvider(): Pair<ComponentType, Provider> =
    when (type) {
        "Bower" -> ComponentType.GIT to Provider.GITHUB
        "Bundler" -> ComponentType.GEM to Provider.RUBYGEMS
        "Cargo" -> ComponentType.CRATE to Provider.CRATES_IO
        "CocoaPods" -> ComponentType.POD to Provider.COCOAPODS
        "DotNet", "nuget" -> ComponentType.NUGET to Provider.NUGET
        "GoDep", "GoMod" -> ComponentType.GIT to Provider.GITHUB
        "Maven" -> ComponentType.MAVEN to Provider.MAVEN_CENTRAL
        "NPM" -> ComponentType.NPM to Provider.NPM_JS
        "PhpComposer" -> ComponentType.COMPOSER to Provider.PACKAGIST
        "PyPI" -> ComponentType.PYPI to Provider.PYPI
        "Pub" -> ComponentType.GIT to Provider.GITHUB
        else -> throw IllegalArgumentException("Unknown mapping of ORT type '$type' to ClearlyDefined.")
    }

/**
 * Map an ORT [Identifier] to ClearlyDefined [Coordinates].
 */
fun Identifier.toClearlyDefinedCoordinates(): Coordinates {
    val (type, provider) = toClearlyDefinedTypeAndProvider()

    return Coordinates(
        type = type,
        provider = provider,
        namespace = namespace.takeUnless { it.isEmpty() },
        name = name,
        revision = version.takeUnless { it.isEmpty() }
    )
}

/**
 * Create a ClearlyDefined [SourceLocation] from an [Identifier] preferably a [VcsInfoCurationData], but eventually fall
 * back to a [RemoteArtifact], or return null if neither is specified.
 */
fun toClearlyDefinedSourceLocation(
    id: Identifier,
    vcs: VcsInfoCurationData?,
    sourceArtifact: RemoteArtifact?
): SourceLocation? {
    val vcsUrl = vcs?.url
    val vcsRevision = vcs?.revision

    return when {
        // GitHub is the only VCS provider supported by ClearlyDefined for now.
        // TODO: Find out how to handle VCS curations without a revision.
        vcsUrl != null && VcsHost.GITHUB.isApplicable(vcsUrl) && vcsRevision != null -> {
            SourceLocation(
                name = id.name,
                namespace = id.namespace,
                path = vcs.path,
                provider = Provider.GITHUB,
                revision = vcsRevision,
                type = ComponentType.GIT,
                url = vcsUrl
            )
        }

        sourceArtifact != null -> {
            val (_, provider) = id.toClearlyDefinedTypeAndProvider()

            SourceLocation(
                name = id.name,
                namespace = id.namespace.takeUnless { it.isEmpty() },
                provider = provider,
                revision = id.version,
                type = ComponentType.SOURCE_ARCHIVE,
                url = sourceArtifact.url
            )
        }

        else -> null
    }
}

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
        val namespace = pkgId.namespace.takeUnless { it.isEmpty() } ?: "-"
        val (type, provider) = pkgId.toClearlyDefinedTypeAndProvider()
        val curationCall = service.getCuration(type, provider, namespace, pkgId.name, pkgId.version)

        val response = try {
            curationCall.execute()
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Getting curations for '${pkgId.toCoordinates()}' failed with: ${e.collectMessagesAsString()}" }

            null
        }

        val curation = response?.body() ?: return emptyList()

        val sourceLocation = curation.described?.sourceLocation.toArtifactOrVcs()
        val pkgCuration = PackageCuration(
            id = pkgId,
            data = PackageCurationData(
                declaredLicenses = curation.licensed?.declared?.let { sortedSetOf(it) },
                homepageUrl = curation.described?.projectWebsite?.toString(),
                sourceArtifact = sourceLocation as? RemoteArtifact,
                vcs = sourceLocation as? VcsInfoCurationData,
                comment = "Provided by ClearlyDefined."
            )
        )

        return listOf(pkgCuration)
    }
}
