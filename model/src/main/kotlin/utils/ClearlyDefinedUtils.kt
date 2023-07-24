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
 * Determine the ClearlyDefined [Provider] based on a [Package]'s location as defined by the [RemoteArtifact] URLs or
 * the [VcsInfo] URL. Return null if a mapping is not possible.
 */
fun Package.toClearlyDefinedProvider(): Provider? =
    sequenceOf(
        binaryArtifact.url,
        sourceArtifact.url,
        vcsProcessed.url
    ).firstNotNullOfOrNull { url ->
        PackageProvider.get(url)?.let { provider ->
            Provider.entries.find { it.name == provider.name }
        }
    }

/**
 * Map an ORT [Package] to ClearlyDefined [Coordinates], or to null if a mapping is not possible.
 */
fun Package.toClearlyDefinedCoordinates(): Coordinates? {
    val type = id.toClearlyDefinedType() ?: return null
    val provider = toClearlyDefinedProvider() ?: type.defaultProvider ?: return null

    return Coordinates(
        type = type,
        provider = provider,
        namespace = id.namespace.takeUnless { it.isEmpty() },
        name = id.name,
        revision = id.version.takeUnless { it.isEmpty() }
    )
}

/**
 * Create a ClearlyDefined [SourceLocation] from a [Package]. Prefer [VcsInfo], but eventually fall back to the
 * [RemoteArtifact] for the source code, or return null if not enough information is available.
 */
fun Package.toClearlyDefinedSourceLocation(): SourceLocation? {
    val coordinates = toClearlyDefinedCoordinates() ?: return null

    return when {
        // TODO: Find out how to handle VCS curations without a revision.
        vcsProcessed != VcsInfo.EMPTY -> {
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

        sourceArtifact != RemoteArtifact.EMPTY -> {
            SourceLocation(
                type = ComponentType.SOURCE_ARCHIVE,
                provider = coordinates.provider,
                namespace = coordinates.namespace,
                name = coordinates.name,

                revision = id.version,

                url = sourceArtifact.url
            )
        }

        else -> null
    }
}
