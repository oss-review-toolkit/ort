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

import java.net.URI

import org.ossreviewtoolkit.clients.clearlydefined.ComponentType
import org.ossreviewtoolkit.clients.clearlydefined.Coordinates
import org.ossreviewtoolkit.clients.clearlydefined.Provider
import org.ossreviewtoolkit.clients.clearlydefined.SourceLocation
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageProvider
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.utils.common.percentEncode

internal fun TextLocation.prependPath(prefix: String): String =
    if (prefix.isBlank()) path else "${prefix.removeSuffix("/")}/$path"

/**
 * Map the [type][Identifier.type] of a [package identifier][Package.id] to a ClearlyDefined [ComponentType], or return
 * null if a mapping is not possible.
 */
fun Package.toClearlyDefinedType(): ComponentType? =
    when (id.type) {
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
            Provider.values().find { it.name == provider.name }
        }
    }

/**
 * Map an ORT [Package] to ClearlyDefined [Coordinates], or to null if a mapping is not possible.
 */
fun Package.toClearlyDefinedCoordinates(): Coordinates? {
    val type = toClearlyDefinedType() ?: return null
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
        "go" -> PurlType.GOLANG
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
