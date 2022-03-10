/*
 * Copyright (C) 2020-2021 HERE Europe B.V.
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

import java.io.File
import java.net.URI

import org.ossreviewtoolkit.clients.clearlydefined.ComponentType
import org.ossreviewtoolkit.clients.clearlydefined.Coordinates
import org.ossreviewtoolkit.clients.clearlydefined.Provider
import org.ossreviewtoolkit.clients.clearlydefined.SourceLocation
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfoCurationData
import org.ossreviewtoolkit.utils.common.percentEncode

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
        "Composer" -> ComponentType.COMPOSER to Provider.PACKAGIST
        "Crate" -> ComponentType.CRATE to Provider.CRATES_IO
        "DotNet", "NuGet" -> ComponentType.NUGET to Provider.NUGET
        "Gem" -> ComponentType.GEM to Provider.RUBYGEMS
        "GoDep", "GoMod" -> ComponentType.GO to Provider.GOLANG
        "Maven" -> ComponentType.MAVEN to Provider.MAVEN_CENTRAL
        "NPM" -> ComponentType.NPM to Provider.NPM_JS
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
    val vcsRevision = vcs?.revision
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

/**
 * A subset of the Package URL types defined at https://github.com/package-url/purl-spec/blob/ad8a673/PURL-TYPES.rst.
 */
enum class PurlType(private val value: String) {
    ALPINE("alpine"),
    A_NAME("a-name"),
    BOWER("bower"),
    CARGO("cargo"),
    COCOAPODS("cocoapods"),
    COMPOSER("composer"),
    CONAN("conan"),
    CONDA("conda"),
    CRAN("cran"),
    DEBIAN("deb"),
    DRUPAL("drupal"),
    GEM("gem"),
    GOLANG("golang"),
    MAVEN("maven"),
    NPM("npm"),
    NUGET("nuget"),
    PYPI("pypi"),
    RPM("rpm");

    override fun toString() = value
}

/**
 * Map a [Package]'s type to the string representation of the respective [PurlType], or fall back to the lower-case
 * [Package]'s type if the [PurlType] cannot be determined.
 */
fun Identifier.getPurlType() =
    when (val lowerType = type.lowercase()) {
        "bower" -> PurlType.BOWER
        "composer" -> PurlType.COMPOSER
        "conan" -> PurlType.CONAN
        "crate" -> PurlType.CARGO
        "godep", "gomod" -> PurlType.GOLANG
        "gem" -> PurlType.GEM
        "maven" -> PurlType.MAVEN
        "npm" -> PurlType.NPM
        "nuget" -> PurlType.NUGET
        "pypi" -> PurlType.PYPI
        else -> lowerType
    }.toString()

/**
 * Create the canonical [package URL](https://github.com/package-url/purl-spec) ("purl") based on the properties of
 * the [Identifier]. Some issues remain with this specification
 * (see e.g. https://github.com/package-url/purl-spec/issues/33).
 *
 * This implementation uses the package type as 'type' purl element as it is used
 * [in the documentation](https://github.com/package-url/purl-spec/blob/master/README.rst#purl).
 * E.g. 'maven' for Gradle projects.
 */
fun Identifier.toPurl() = if (this == Identifier.EMPTY) "" else createPurl(getPurlType(), namespace, name, version)

/**
 * Create the canonical [package URL](https://github.com/package-url/purl-spec) ("purl") based on given properties:
 * [type] (which must be a String representation of a [PurlType] instance, [namespace], [name] and [version].
 * Optional [qualifiers] may be given and will be appended to the purl as query parameters e.g.
 * pkg:deb/debian/curl@7.50.3-1?arch=i386&distro=jessie
 * Optional [subpath] may be given and will be appended to the purl e.g.
 * pkg:golang/google.golang.org/genproto#googleapis/api/annotations
 *
 */
fun createPurl(
    type: String,
    namespace: String,
    name: String,
    version: String,
    qualifiers: Map<String, String> = emptyMap(),
    subpath: String = ""
): String = buildString {
    append("pkg:")
    append(type)

    if (namespace.isNotEmpty()) {
        append('/')
        append(namespace.percentEncode())
    }

    append('/')
    append(name.percentEncode())

    append('@')
    append(version.percentEncode())

    qualifiers.onEachIndexed { index, entry ->
        if (index == 0) append("?") else append("&")
        append(entry.key.percentEncode())
        append("=")
        append(entry.value.percentEncode())
    }

    if (subpath.isNotEmpty()) {
        val value = subpath.split('/').joinToString("/", prefix = "#") { it.percentEncode() }
        append(value)
    }
}

/**
 * Return a list of [ScanResult]s where all results contain only findings from the same directory as the [project]'s
 * definition file.
 */
fun List<ScanResult>.filterByProject(project: Project): List<ScanResult> {
    val parentPath = File(project.definitionFilePath).parent ?: return this

    return map { result ->
        if (result.provenance is ArtifactProvenance) {
            result
        } else {
            result.filterByPath(parentPath)
        }
    }
}

/**
 * Return the repo manifest path parsed from this string. The string is interpreted as a URL and the manifest path is
 * expected as the value of the "manifest" query parameter, for example:
 * http://example.com/repo.git?manifest=manifest.xml.
 *
 * Return an empty string if no "manifest" query parameter is found or this string cannot be parsed as a URL.
 */
fun String.parseRepoManifestPath() =
    runCatching {
        URI(this).query.splitToSequence("&")
            .map { it.split("=", limit = 2) }
            .find { it.first() == "manifest" }
            ?.get(1)
            ?.takeUnless { it.isEmpty() }
    }.getOrNull()

/**
 * Messages are not rendered using additional white spaces and newlines in the reports. However, resolutions are based
 * on the messages. Therefore, characters that are not shown in the reports need to be replaced in the comparison.
 */
fun String.sanitizeMessage() = replace(Regex("\\s+"), " ").trim()
