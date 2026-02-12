/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import com.github.packageurl.PackageURL
import com.github.packageurl.PackageURLBuilder

import java.io.File

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

/**
 * Map a [Package]'s type to the string representation of the respective [PurlType], or fall back to [PurlType.GENERIC]
 * if the [Package]'s type has no direct equivalent.
 */
fun Identifier.getPurlType() =
    when (type.lowercase()) {
        "bazel" -> PurlType.BAZEL
        "bower" -> PurlType.BOWER
        "carthage" -> PurlType.CARTHAGE
        "composer" -> PurlType.COMPOSER
        "conan" -> PurlType.CONAN
        "crate" -> PurlType.CARGO
        "gem" -> PurlType.GEM
        "go" -> PurlType.GOLANG
        "hackage" -> PurlType.HACKAGE
        "hex" -> PurlType.HEX
        "maven" -> PurlType.MAVEN
        "npm" -> PurlType.NPM
        "nuget" -> PurlType.NUGET
        "otp", "gleam" -> PurlType.OTP
        "pod" -> PurlType.COCOAPODS
        "pub" -> PurlType.PUB
        "pypi" -> PurlType.PYPI
        "swift" -> PurlType.SWIFT
        else -> PurlType.GENERIC
    }

/**
 * Map a [PurlType] to the corresponding ORT Identifier type string.
 *
 * This is the inverse operation of [Identifier.getPurlType].
 * It converts a [PurlType] enum value to the corresponding ORT
 * type string format.
 */
fun PurlType.toOrtType(): String =
    when (this) {
        PurlType.BAZEL -> "bazel"
        PurlType.BOWER -> "bower"
        PurlType.CARTHAGE -> "carthage"
        PurlType.COMPOSER -> "composer"
        PurlType.CONAN -> "conan"
        PurlType.CARGO -> "crate"
        PurlType.GEM -> "gem"
        PurlType.GOLANG -> "go"
        PurlType.HACKAGE -> "hackage"
        PurlType.HEX -> "hex"
        PurlType.MAVEN -> "Maven"
        PurlType.NPM -> "npm"
        PurlType.NUGET -> "nuget"
        PurlType.OTP -> "otp"
        PurlType.COCOAPODS -> "pod"
        PurlType.PUB -> "pub"
        PurlType.PYPI -> "pypi"
        PurlType.SWIFT -> "swift"
        PurlType.GENERIC -> "generic"
        else -> toString().lowercase()
    }

/**
 * Create the canonical [package URL](https://github.com/package-url/purl-spec) ("purl") based on the properties of
 * the [Identifier].
 * Optional [qualifiers] may be given and will be appended to the purl as query parameters e.g.
 * pkg:deb/debian/curl@7.50.3-1?arch=i386&distro=jessie
 * Optional [subpath] may be given and will be appended to the purl e.g.
 * pkg:golang/google.golang.org/genproto#googleapis/api/annotations
 */
@JvmOverloads
fun Identifier.toPurl(qualifiers: Map<String, String> = emptyMap(), subpath: String = ""): String {
    if (this == Identifier.EMPTY) return ""

    val type = getPurlType()
    val combined = "$namespace/$name"
    val purlNamespace = combined.substringBeforeLast('/')
    val purlName = combined.substringAfterLast('/')

    // Apply ORT-specific normalizations BEFORE passing to builder.
    val normalizedNamespace = purlNamespace.trim('/').split('/').joinToString("/") { segment ->
        type.namespaceNormalization(segment)
    }.takeIf { it.isNotEmpty() }

    val normalizedName = type.nameNormalization(purlName.trim('/'))

    val normalizedSubpath = if (subpath.isNotEmpty()) {
        subpath.trim('/').split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") {
                // Instead of just discarding "." and "..", resolve them by normalizing.
                File(it).normalize().path
            }
            .takeIf { it.isNotEmpty() }
    } else {
        null
    }

    return PackageURLBuilder.aPackageURL()
        .withType(type.toString())
        .withNamespace(normalizedNamespace)
        .withName(normalizedName)
        .withVersion(version.takeIf { it.isNotEmpty() })
        .apply {
            qualifiers.filterValues { it.isNotEmpty() }.forEach { (key, value) ->
                withQualifier(key, value)
            }
        }
        .withSubpath(normalizedSubpath)
        .build()
        .canonicalize()
}

/**
 * Create a PURL for this [Identifier] with qualifiers and subpath derived from [provenance].
 */
fun Identifier.toPurl(provenance: Provenance): String =
    when (provenance) {
        is ArtifactProvenance -> {
            val hash = provenance.sourceArtifact.hash
            val qualifiers = buildMap {
                put("download_url", provenance.sourceArtifact.url)

                if (hash.algorithm.isVerifiable) {
                    put("checksum", "${hash.algorithm.name.lowercase()}:${hash.value}")
                }
            }

            toPurl(qualifiers = qualifiers)
        }

        is RepositoryProvenance -> {
            toPurl(
                qualifiers = mapOf(
                    "vcs_type" to provenance.vcsInfo.type.toString(),
                    "vcs_url" to provenance.vcsInfo.url,
                    "vcs_revision" to provenance.vcsInfo.revision,
                    "resolved_revision" to provenance.resolvedRevision
                ),
                subpath = provenance.vcsInfo.path
            )
        }

        is UnknownProvenance -> toPurl()
    }

/**
 * Convert this [PackageURL] to an ORT [Identifier].
 */
fun PackageURL.toIdentifier(): Identifier {
    val ortType = getPurlType()?.toOrtType() ?: type
    val combinedName = listOfNotNull(namespace, name).joinToString("/")

    return Identifier(
        type = ortType,
        namespace = combinedName.substringBeforeLast("/", ""),
        name = combinedName.substringAfterLast("/"),
        version = version.orEmpty()
    )
}

/**
 * Decode [Provenance] from extra qualifying data / a subpath of this [PackageURL]. Return [UnknownProvenance] if
 * extra data is not present.
 */
fun PackageURL.toProvenance(): Provenance {
    val qualifiers = qualifiers.orEmpty()

    return when {
        "download_url" in qualifiers -> {
            val checksum = qualifiers["checksum"].orEmpty()
            val (algorithm, value) = checksum.split(':', limit = 2)

            ArtifactProvenance(
                sourceArtifact = RemoteArtifact(
                    url = qualifiers["download_url"].orEmpty(),
                    hash = Hash(value, algorithm)
                )
            )
        }

        "vcs_url" in qualifiers -> {
            RepositoryProvenance(
                vcsInfo = VcsInfo(
                    type = VcsType.forName(qualifiers["vcs_type"].orEmpty()),
                    url = qualifiers["vcs_url"].orEmpty(),
                    revision = qualifiers["vcs_revision"].orEmpty(),
                    path = subpath.orEmpty()
                ),
                resolvedRevision = qualifiers["resolved_revision"].orEmpty()
            )
        }

        else -> UnknownProvenance
    }
}

/**
 * Parse a PURL string into a [PackageURL] object.
 * Returns null if the string is null, blank, or invalid.
 */
fun String?.toPackageUrl(): PackageURL? = runCatching { PackageURL(this) }.getOrNull()

/**
 * Get the [PurlType] enum for this [PackageURL], or null if the type is unknown.
 */
fun PackageURL.getPurlType(): PurlType? = runCatching { PurlType.fromString(type.lowercase()) }.getOrNull()
