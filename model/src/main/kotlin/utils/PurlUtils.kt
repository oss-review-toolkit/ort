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

import com.github.packageurl.PackageURL
import com.github.packageurl.PackageURLBuilder

import java.io.File

/**
 * A subset of the Package URL types defined at https://github.com/package-url/purl-spec/blob/ad8a673/PURL-TYPES.rst.
 */
enum class PurlType(
    private val value: String,
    val nameNormalization: (String) -> String = { it },
    val namespaceNormalization: (String) -> String = { it }
) {
    APK("apk", { it.lowercase() }, { it.lowercase() }),
    BAZEL("bazel", { it.lowercase() }),
    BITBUCKET("bitbucket", { it.lowercase() }, { it.lowercase() }),
    BOWER("bower"),
    CARGO("cargo"),
    CARTHAGE("carthage"),
    COCOAPODS("cocoapods"),
    COMPOSER("composer", { it.lowercase() }, { it.lowercase() }),
    CONAN("conan"),
    CONDA("conda"),
    CRAN("cran"),
    DEBIAN("deb", { it.lowercase() }, { it.lowercase() }),
    DOCKER("docker"),
    DRUPAL("drupal"),
    GEM("gem"),
    GENERIC("generic"),
    GITHUB("github", { it.lowercase() }, { it.lowercase() }),
    GITLAB("gitlab"),
    GOLANG("golang", { it.lowercase() }, { it.lowercase() }),
    HACKAGE("hackage"),
    HEX("hex"),
    HUGGING_FACE("huggingface"),
    MAVEN("maven"),
    MLFLOW("mlflow"),
    NPM("npm", { it.lowercase() }),
    NUGET("nuget"),
    OTP("otp"),
    PUB("pub", { it.lowercase() }),
    PYPI("pypi", { it.lowercase() }),
    RPM("rpm", namespaceNormalization = { it.lowercase() }),
    SWIFT("swift");

    init {
        check(value == value.lowercase()) { "The type must be in canonical lowercase form." }
    }

    companion object {
        @JvmStatic
        fun fromString(value: String): PurlType =
            PurlType.entries.find { it.value == value } ?: throw IllegalArgumentException("Unknown purl type: $value")
    }

    override fun toString() = value
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
): String {
    // Apply ORT-specific normalizations BEFORE passing to builder.
    val normalizedNamespace = namespace.trim('/').split('/').joinToString("/") { segment ->
        type.namespaceNormalization(segment)
    }.takeIf { it.isNotEmpty() }

    val normalizedName = type.nameNormalization(name.trim('/'))

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
 * Parse a PURL string into a [PackageURL] object.
 * Returns null if the string is null, blank, or invalid.
 */
fun String?.toPackageUrl(): PackageURL? {
    if (this.isNullOrBlank()) return null
    return runCatching { PackageURL(this) }.getOrNull()
}

/**
 * Get the [PurlType] enum for this [PackageURL], or null if the type is unknown.
 */
fun PackageURL.getPurlType(): PurlType? = runCatching { PurlType.fromString(type.lowercase()) }.getOrNull()
