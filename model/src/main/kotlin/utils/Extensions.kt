/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.Coordinates
import org.ossreviewtoolkit.clearlydefined.ClearlyDefinedService.SourceLocation
import org.ossreviewtoolkit.clearlydefined.ComponentType
import org.ossreviewtoolkit.clearlydefined.Provider
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfoCurationData

internal fun TextLocation.prependPath(prefix: String): String =
    if (prefix.isBlank()) path else "${prefix.removeSuffix("/")}/$path"

/**
 * Map an [Identifier] to a ClearlyDefined [ComponentType] and [Provider]. Note that an
 * [identifier's type][Identifier.type] in ORT currently implies a default provider. Return null if a mapping is not
 * possible.
 */
fun Identifier.toClearlyDefinedTypeAndProvider(): Pair<ComponentType, Provider>? =
    when (type) {
        "Bower" -> ComponentType.GIT to Provider.GITHUB
        "CocoaPods" -> ComponentType.POD to Provider.COCOAPODS
        "Crate" -> ComponentType.CRATE to Provider.CRATES_IO
        "DotNet", "NuGet" -> ComponentType.NUGET to Provider.NUGET
        "Gem" -> ComponentType.GEM to Provider.RUBYGEMS
        "GoDep", "GoMod" -> ComponentType.GIT to Provider.GITHUB
        "Maven" -> ComponentType.MAVEN to Provider.MAVEN_CENTRAL
        "NPM" -> ComponentType.NPM to Provider.NPM_JS
        "PhpComposer" -> ComponentType.COMPOSER to Provider.PACKAGIST
        "PyPI" -> ComponentType.PYPI to Provider.PYPI
        "Pub" -> ComponentType.GIT to Provider.GITHUB
        else -> null
    }

/**
 * Map an ORT [Identifier] to ClearlyDefined [Coordinates], or to null if mapping is not possible.
 */
fun Identifier.toClearlyDefinedCoordinates(): Coordinates? =
    toClearlyDefinedTypeAndProvider()?.let { (type, provider) ->
        Coordinates(
            type = type,
            provider = provider,
            namespace = namespace.takeUnless { it.isEmpty() },
            name = name,
            revision = version.takeUnless { it.isEmpty() }
        )
    }

/** Regular expression to match VCS URLs supported by ClearlyDefined. */
private val REG_GIT_URL = Regex(".+://github.com/(.+)/(.+).git")

/**
 * Create a ClearlyDefined [SourceLocation] from an [Identifier]. Prefer a [VcsInfoCurationData], but eventually fall
 * back to a [RemoteArtifact], or return null if not enough information is available.
 */
fun Identifier.toClearlyDefinedSourceLocation(
    vcs: VcsInfoCurationData?,
    sourceArtifact: RemoteArtifact?
): SourceLocation? {
    val vcsUrl = vcs?.url
    val vcsRevision = vcs?.resolvedRevision
    val matchGroups = vcsUrl?.let { REG_GIT_URL.matchEntire(it)?.groupValues }

    return when {
        // GitHub is the only VCS provider supported by ClearlyDefined for now.
        // TODO: Find out how to handle VCS curations without a revision.
        vcsUrl != null && matchGroups != null && vcsRevision != null -> {
            SourceLocation(
                name = matchGroups[2],
                namespace = matchGroups[1],
                path = vcs.path,
                provider = Provider.GITHUB,
                revision = vcsRevision,
                type = ComponentType.GIT,
                url = vcsUrl
            )
        }

        sourceArtifact != null -> {
            toClearlyDefinedTypeAndProvider()?.let { (_, provider) ->
                SourceLocation(
                    name = name,
                    namespace = namespace.takeUnless { it.isEmpty() },
                    provider = provider,
                    revision = version,
                    type = ComponentType.SOURCE_ARCHIVE,
                    url = sourceArtifact.url
                )
            }
        }

        else -> null
    }
}
