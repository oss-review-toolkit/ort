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

@file:Suppress("MatchingDeclarationName")

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.utils.common.percentEncode

/**
 * A subset of the Package URL types defined at https://github.com/package-url/purl-spec/blob/ad8a673/PURL-TYPES.rst.
 */
enum class PurlType(private val value: String) {
    ALPINE("alpine"),
    A_NAME("a-name"),
    BOWER("bower"),
    CARGO("cargo"),
    CARTHAGE("carthage"),
    COCOAPODS("cocoapods"),
    COMPOSER("composer"),
    CONAN("conan"),
    CONDA("conda"),
    CRAN("cran"),
    DEBIAN("deb"),
    DRUPAL("drupal"),
    GEM("gem"),
    GENERIC("generic"),
    GITHUB("github"),
    GITLAB("gitlab"),
    GOLANG("golang"),
    HACKAGE("hackage"),
    MAVEN("maven"),
    NPM("npm"),
    NUGET("nuget"),
    PUB("pub"),
    PYPI("pypi"),
    RPM("rpm"),
    SWIFT("swift");

    override fun toString() = value
}

/**
 * Extra data than can be appended to a "clean" PURL via qualifiers or a subpath.
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
 * [type] (which must be a String representation of a [PurlType] instance, [namespace], [name] and [version].
 * Optional [qualifiers] may be given and will be appended to the purl as query parameters e.g.
 * pkg:deb/debian/curl@7.50.3-1?arch=i386&distro=jessie
 * Optional [subpath] may be given and will be appended to the purl e.g.
 * pkg:golang/google.golang.org/genproto#googleapis/api/annotations
 *
 */
internal fun createPurl(
    type: String,
    namespace: String,
    name: String,
    version: String,
    qualifiers: Map<String, String> = emptyMap(),
    subpath: String = ""
): String =
    buildString {
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
