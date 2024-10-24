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

import java.net.URLDecoder

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
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
        "bower" -> PurlType.BOWER
        "carthage" -> PurlType.CARTHAGE
        "composer" -> PurlType.COMPOSER
        "conan" -> PurlType.CONAN
        "crate" -> PurlType.CARGO
        "go" -> PurlType.GOLANG
        "gem" -> PurlType.GEM
        "hackage" -> PurlType.HACKAGE
        "maven" -> PurlType.MAVEN
        "npm" -> PurlType.NPM
        "nuget" -> PurlType.NUGET
        "pod" -> PurlType.COCOAPODS
        "pub" -> PurlType.PUB
        "pypi" -> PurlType.PYPI
        "swift" -> PurlType.SWIFT
        else -> PurlType.GENERIC
    }.toString()

/**
 * Create the canonical [package URL](https://github.com/package-url/purl-spec) ("purl") based on the properties of
 * the [Identifier]. Some issues remain with this specification
 * (see e.g. https://github.com/package-url/purl-spec/issues/33).
 * Optional [qualifiers] may be given and will be appended to the purl as query parameters e.g.
 * pkg:deb/debian/curl@7.50.3-1?arch=i386&distro=jessie
 * Optional [subpath] may be given and will be appended to the purl e.g.
 * pkg:golang/google.golang.org/genproto#googleapis/api/annotations
 *
 * This implementation uses the package type as 'type' purl element as it is used
 * [in the documentation](https://github.com/package-url/purl-spec/blob/master/README.rst#purl).
 * E.g. 'maven' for Gradle projects.
 */
@JvmOverloads
fun Identifier.toPurl(qualifiers: Map<String, String> = emptyMap(), subpath: String = "") =
    if (this == Identifier.EMPTY) {
        ""
    } else {
        val combined = "$namespace/$name"
        val purlNamespace = combined.substringBeforeLast('/')
        val purlName = combined.substringAfterLast('/')
        createPurl(getPurlType(), purlNamespace, purlName, version, qualifiers, subpath)
    }

fun Identifier.toPurl(extras: PurlExtras) = toPurl(extras.qualifiers, extras.subpath)

/**
 * Encode a [Provenance] to extra qualifying data / a subpath of PURL.
 */
fun Provenance.toPurlExtras(): PurlExtras =
    when (this) {
        is ArtifactProvenance -> with(sourceArtifact) {
            val checksum = "${hash.algorithm.name.lowercase()}:${hash.value}"
            PurlExtras(
                "download_url" to url,
                "checksum" to checksum
            )
        }

        is RepositoryProvenance -> with(vcsInfo) {
            PurlExtras(
                "vcs_type" to type.toString(),
                "vcs_url" to url,
                "vcs_revision" to revision,
                "resolved_revision" to resolvedRevision,
                subpath = vcsInfo.path
            )
        }

        is UnknownProvenance -> PurlExtras()
    }

/**
 * Decode [Provenance] from extra qualifying data / a subpath of the purl represented by this [String]. Return
 * [UnknownProvenance] if extra data is not present.
 */
fun String.toProvenance(): Provenance {
    val extras = substringAfter('?')

    fun getQualifierValue(name: String) = extras.substringAfter("$name=").takeWhile { it != '&' && it != '#' }

    return when {
        "download_url=" in extras -> {
            val encodedUrl = getQualifierValue("download_url")

            val checksum = getQualifierValue("checksum")
            val (algorithm, value) = checksum.split(':', limit = 2)

            ArtifactProvenance(
                sourceArtifact = RemoteArtifact(
                    url = URLDecoder.decode(encodedUrl, "UTF-8"),
                    hash = Hash(value, algorithm)
                )
            )
        }

        "vcs_url=" in extras -> {
            val encodedUrl = getQualifierValue("vcs_url")

            RepositoryProvenance(
                vcsInfo = VcsInfo(
                    type = VcsType.forName(getQualifierValue("vcs_type")),
                    url = URLDecoder.decode(encodedUrl, "UTF-8"),
                    revision = getQualifierValue("vcs_revision"),
                    path = extras.substringAfterLast('#', "")
                ),
                resolvedRevision = getQualifierValue("resolved_revision")
            )
        }

        else -> UnknownProvenance
    }
}
