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
 * Map an ORT [Identifier] type to the corresponding PURL type string, or fall back to "generic"
 * if there is no direct equivalent.
 */
fun Identifier.getPurlType(): String =
    when (type.lowercase()) {
        "bazel" -> "bazel"
        "bower" -> "bower"
        "carthage" -> "carthage"
        "composer" -> "composer"
        "conan" -> "conan"
        "crate" -> "cargo"
        "gem" -> "gem"
        "go" -> "golang"
        "hackage" -> "hackage"
        "hex" -> "hex"
        "maven" -> "maven"
        "npm" -> "npm"
        "nuget" -> "nuget"
        "otp", "gleam" -> "otp"
        "pod" -> "cocoapods"
        "pub" -> "pub"
        "pypi" -> "pypi"
        "swift" -> "swift"
        else -> "generic"
    }

/**
 * Map a PURL type string to the corresponding ORT Identifier type string.
 */
internal fun purlTypeToOrtType(purlType: String): String =
    when (purlType) {
        "cargo" -> "crate"
        "cocoapods" -> "pod"
        "golang" -> "go"
        "maven" -> "Maven"
        else -> purlType
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
    val namespace = combined.substringBeforeLast('/').trim('/').takeIf { it.isNotEmpty() }
    val name = combined.substringAfterLast('/').trim('/')

    // Avoid a `MalformedPackageURLException` and behave like for `Identifier.EMPTY` in case of exotic packages like
    // Pub SDK packages (e.g., sky_engine) where the name is not explicitly set.
    if (name.isEmpty()) return ""

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
        .withType(type)
        .withNamespace(namespace)
        .withName(name)
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
    val ortType = purlTypeToOrtType(type)
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
