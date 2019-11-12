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

package com.here.ort.analyzer.curation

import com.here.ort.analyzer.PackageCurationProvider
import com.here.ort.clearlydefined.ClearlyDefinedService
import com.here.ort.clearlydefined.ClearlyDefinedService.Coordinates
import com.here.ort.clearlydefined.ClearlyDefinedService.Provider
import com.here.ort.clearlydefined.ClearlyDefinedService.Server
import com.here.ort.clearlydefined.ClearlyDefinedService.SourceLocation
import com.here.ort.clearlydefined.ClearlyDefinedService.Type
import com.here.ort.downloader.VcsHost
import com.here.ort.model.Identifier
import com.here.ort.model.Hash
import com.here.ort.model.PackageCuration
import com.here.ort.model.PackageCurationData
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfoCuration
import com.here.ort.model.VcsType

/**
 * Map an [Identifier] to a ClearlyDefined [Type] and [Provider]. Note that an Identifier's type in ORT currently
 * implies a default provider.
 */
fun Identifier.toClearlyDefinedTypeAndProvider(): Pair<Type, Provider> =
    when (type) {
        "Bower" -> Type.GIT to Provider.GITHUB
        "Bundler" -> Type.GEM to Provider.RUBYGEMS
        "Cargo" -> Type.CRATE to Provider.CRATES_IO
        "CocoaPods" -> Type.POD to Provider.COCOAPODS
        "nuget" -> Type.NUGET to Provider.NUGET
        "GoDep" -> Type.GIT to Provider.GITHUB
        "Maven" -> Type.MAVEN to Provider.MAVEN_CENTRAL
        "NPM" -> Type.NPM to Provider.NPM_JS
        "PhpComposer" -> Type.COMPOSER to Provider.PACKAGIST
        "PyPI" -> Type.PYPI to Provider.PYPI
        "Pub" -> Type.GIT to Provider.GITHUB
        else -> throw IllegalArgumentException("Unknown mapping of ORT type '$type' to ClearlyDefined.")
    }

/**
 * Map an ORT [Package id][pkgId] to ClearlyDefined [Coordinates].
 */
fun Identifier.toClearlyDefinedCoordinates(): Coordinates {
    val (type, provider) = toClearlyDefinedTypeAndProvider()

    return Coordinates(
        name = name,
        namespace = namespace.takeUnless { it.isEmpty() },
        provider = provider,
        type = type
    )
}

/**
 * Create a ClearlyDefined [SourceLocation] from an [Identifier] preferably a [VcsInfoCuration], but eventually fall
 * back to a [RemoteArtifact], or return null if neither is specified.
 */
fun toClearlyDefinedSourceLocation(
    id: Identifier,
    vcs: VcsInfoCuration?,
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
                type = Type.GIT,
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
                type = Type.SOURCE_ARCHIVE,
                url = sourceArtifact.url
            )
        }

        else -> null
    }
}

/**
 * Map a ClearlyDefined [SourceLocation] to either a [VcsInfoCuration] or a [RemoteArtifact].
 */
fun SourceLocation?.toArtifactOrVcs(): Any? =
    this?.let { sourceLocation ->
        when (sourceLocation.type) {
            Type.GIT -> {
                VcsInfoCuration(
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
    private val service = ClearlyDefinedService.create(server)

    override fun getCurationsFor(pkgId: Identifier): List<PackageCuration> {
        val namespace = pkgId.namespace.takeUnless { it.isEmpty() } ?: "-"
        val (type, provider) = pkgId.toClearlyDefinedTypeAndProvider()
        val curationCall = service.getCuration(type, provider, namespace, pkgId.name, pkgId.version)

        val curation = curationCall.execute().body() ?: return emptyList()

        val sourceLocation = curation.described?.sourceLocation.toArtifactOrVcs()
        val pkgCuration = PackageCuration(
            id = pkgId,
            data = PackageCurationData(
                declaredLicenses = curation.licensed?.declared?.let { sortedSetOf(it) },
                homepageUrl = curation.described?.projectWebsite?.toString(),
                sourceArtifact = sourceLocation as? RemoteArtifact,
                vcs = sourceLocation as? VcsInfoCuration,
                comment = "Provided by ClearlyDefined."
            )
        )

        return listOf(pkgCuration)
    }
}
