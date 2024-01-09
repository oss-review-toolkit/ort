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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package

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
        "spm" -> PurlType.SWIFT
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
    if (this == Identifier.EMPTY) "" else createPurl(getPurlType(), namespace, name, version, qualifiers, subpath)
