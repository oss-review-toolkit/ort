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

@file:Suppress("MatchingDeclarationName")

package org.ossreviewtoolkit.model.utils

import java.io.File

import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.utils.common.percentEncode

/**
 * A subset of the Package URL types defined at https://github.com/package-url/purl-spec/blob/ad8a673/PURL-TYPES.rst.
 */
enum class PurlType(
    private val value: String,
    val nameNormalization: (String) -> String = { it },
    val namespaceNormalization: (String) -> String = { it },
    private val ortIdentifierType: String = value.replaceFirstChar { it.uppercase() }
) {
    APK("apk", { it.lowercase() }, { it.lowercase() }),
    BAZEL("bazel", { it.lowercase() }),
    BITBUCKET("bitbucket", { it.lowercase() }, { it.lowercase() }),
    BOWER("bower"),
    CARGO("cargo"),
    CARTHAGE("carthage"),
    COCOAPODS("cocoapods", ortIdentifierType = "CocoaPods"),
    COMPOSER("composer", { it.lowercase() }, { it.lowercase() }),
    CONAN("conan"),
    CONDA("conda"),
    CRAN("cran"),
    DEBIAN("deb", { it.lowercase() }, { it.lowercase() }),
    DOCKER("docker"),
    DRUPAL("drupal"),
    GEM("gem"),
    GENERIC("generic"),
    GITHUB("github", { it.lowercase() }, { it.lowercase() }, ortIdentifierType = "GitHub"),
    GITLAB("gitlab", ortIdentifierType = "GitLab"),
    GOLANG("golang", { it.lowercase() }, { it.lowercase() }, ortIdentifierType = "Go"),
    HACKAGE("hackage"),
    HEX("hex"),
    HUGGING_FACE("huggingface", ortIdentifierType = "HuggingFace"),
    MAVEN("maven"),
    MLFLOW("mlflow", ortIdentifierType = "MlFlow"),
    NPM("npm", { it.lowercase() }, ortIdentifierType = "NPM"),
    NUGET("nuget", ortIdentifierType = "NuGet"),
    OTP("otp"),
    PUB("pub", { it.lowercase() }),
    PYPI("pypi", { it.lowercase() }, ortIdentifierType = "PyPi"),
    RPM("rpm", namespaceNormalization = { it.lowercase() }, ortIdentifierType = "RPM"),
    SWIFT("swift");

    init {
        check(value == value.lowercase()) { "The type must be in canonical lowercase form." }
    }

    companion object {
        @JvmStatic
        fun fromString(value: String): PurlType =
            PurlType.entries.find { it.value == value } ?: throw IllegalArgumentException("Unknown purl type: $value")

        @JvmStatic
        fun getOrtTypeFromPurlType(purlType: String): String =
            PurlType.entries.find { it.value == purlType }?.ortIdentifierType
                ?: purlType.replaceFirstChar { it.uppercase() }
    }

    override fun toString() = value
}

/**
 * Extra data than can be appended to a "clean" purl via qualifiers or a subpath.
 */
data class PurlExtras(
    /**
     * Extra qualifying data as key / value pairs. Needs to be percent-encoded when used in a query string.
     */
    val qualifiers: Map<String, String>,

    /**
     * A subpath relative to the root of the package.
     */
    val subpath: String
) {
    constructor(vararg qualifiers: Pair<String, String>, subpath: String = "") : this(qualifiers.toMap(), subpath)
}

/**
 * Create the canonical [package URL](https://github.com/package-url/purl-spec) ("purl") based on given properties:
 * [type], [namespace], [name] and [version].
 * Optional [qualifiers] may be given and will be appended to the purl as query parameters e.g.
 * pkg:deb/debian/curl@7.50.3-1?arch=i386&distro=jessie
 * Optional [subpath] may be given and will be appended to the purl e.g.
 * pkg:golang/google.golang.org/genproto#googleapis/api/annotations
 */
internal fun createPurl(
    type: PurlType,
    namespace: String,
    name: String,
    version: String,
    qualifiers: Map<String, String> = emptyMap(),
    subpath: String = ""
): String =
    buildString {
        append("pkg:")
        append(type.toString())
        append('/')

        if (namespace.isNotEmpty()) {
            append(
                namespace.trim('/').split('/').joinToString("/") { segment ->
                    type.namespaceNormalization(segment).percentEncode()
                }
            )
            append('/')
        }

        append(type.nameNormalization(name.trim('/')).percentEncode())

        if (version.isNotEmpty()) {
            append('@')

            // See https://github.com/package-url/purl-spec/blob/master/PURL-SPECIFICATION.rst#character-encoding which
            // says "the '#', '?', '@' and ':' characters must NOT be encoded when used as separators".
            val isChecksum = HashAlgorithm.VERIFIABLE.any { version.startsWith("${it.name.lowercase()}:") }
            if (isChecksum) append(version) else append(version.percentEncode())
        }

        qualifiers.filterValues { it.isNotEmpty() }.toSortedMap().onEachIndexed { index, entry ->
            if (index == 0) append("?") else append("&")

            val key = entry.key.lowercase()
            append(key)

            append("=")

            if (key in KNOWN_QUALIFIER_KEYS) append(entry.value) else append(entry.value.percentEncode())
        }

        if (subpath.isNotEmpty()) {
            val value = subpath.trim('/').split('/')
                .filter { it.isNotEmpty() }
                .joinToString("/", prefix = "#") {
                    // Instead of just discarding "." and "..", resolve them by normalizing.
                    File(it).normalize().path.percentEncode()
                }

            append(value)
        }
    }

// See https://github.com/package-url/purl-spec/blob/master/PURL-SPECIFICATION.rst#known-qualifiers-keyvalue-pairs.
private val KNOWN_QUALIFIER_KEYS = setOf("repository_url", "download_url", "vcs_url", "file_name", "checksum")
