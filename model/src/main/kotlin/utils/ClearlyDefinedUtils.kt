/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.clients.clearlydefined.ComponentType
import org.ossreviewtoolkit.clients.clearlydefined.Coordinates
import org.ossreviewtoolkit.clients.clearlydefined.Provider
import org.ossreviewtoolkit.clients.clearlydefined.SourceLocation
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.PackageProvider
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo

/**
 * Map an [Identifier's type][Identifier.type] to a ClearlyDefined [ComponentType], or return null if a mapping is not
 * possible.
 */
fun Identifier.toClearlyDefinedType(): ComponentType? =
    when (type) {
        "Bower" -> ComponentType.GIT
        "CocoaPods" -> ComponentType.POD
        "Composer" -> ComponentType.COMPOSER
        "Crate" -> ComponentType.CRATE
        "Gem" -> ComponentType.GEM
        "Go" -> ComponentType.GO
        "Maven" -> ComponentType.MAVEN
        "NPM" -> ComponentType.NPM
        "NuGet" -> ComponentType.NUGET
        "Pub" -> ComponentType.GIT
        "PyPI" -> ComponentType.PYPI
        else -> null
    }

/**
 * Determine the ClearlyDefined [Provider] based on a URL represented as a [String], or return null if the provider
 * could not be determined.
 */
fun String.toClearlyDefinedProvider(): Provider? =
    PackageProvider.get(this)?.let { provider ->
        // The ClearlyDefined and ORT provider enums use the same names for their entries, so they can be matched.
        Provider.entries.find { it.name == provider.name }
    }

/**
 * Map an ORT [Package] to ClearlyDefined [Coordinates], or to null if a mapping is not possible.
 */
fun Identifier.toClearlyDefinedCoordinates(provider: Provider?): Coordinates? {
    val type = toClearlyDefinedType() ?: return null

    return Coordinates(
        type = type,
        provider = provider ?: type.defaultProvider ?: return null,
        namespace = namespace.takeUnless { it.isEmpty() },
        name = name,
        revision = version.takeUnless { it.isEmpty() }
    )
}

/**
 * Create ClearlyDefined [SourceLocation]s from a [Package]'s [source artifact][Package.sourceArtifact] and / or
 * [VCS information][Package.vcsProcessed].
 */
fun Package.toClearlyDefinedSourceLocations(): Set<SourceLocation> =
    buildSet {
        sourceArtifact.url.toClearlyDefinedProvider()?.let { provider ->
            id.toClearlyDefinedCoordinates(provider)?.let { coordinates ->
                SourceLocation(
                    type = ComponentType.SOURCE_ARCHIVE,

                    provider = coordinates.provider,
                    namespace = coordinates.namespace,
                    name = coordinates.name,

                    revision = id.version,
                    url = sourceArtifact.url
                )
            }
        }?.also { add(it) }

        vcsProcessed.url.toClearlyDefinedProvider()?.let { provider ->
            id.toClearlyDefinedCoordinates(provider)?.let { coordinates ->
                SourceLocation(
                    type = ComponentType.GIT,

                    provider = coordinates.provider,
                    namespace = coordinates.namespace,
                    name = coordinates.name,

                    revision = vcsProcessed.revision,
                    path = vcsProcessed.path,
                    url = vcsProcessed.url
                )
            }
        }?.also { add(it) }
    }

/**
 * Create ClearlyDefined [SourceLocation]s from a [Package]'s [source artifact][Package.sourceArtifact] and / or
 * [VCS information][Package.vcsProcessed].
 */
fun PackageCuration.toClearlyDefinedCoordinates(): Set<Coordinates> =
    setOfNotNull(
        data.sourceArtifact?.url?.toClearlyDefinedProvider()?.let { provider ->
            id.toClearlyDefinedCoordinates(provider)
        },
        data.vcs?.url?.toClearlyDefinedProvider()?.let { provider ->
            id.toClearlyDefinedCoordinates(provider)
        }
    )
