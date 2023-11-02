/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.trustsource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import org.ossreviewtoolkit.model.Project

/**
 * This class holds information about what dependencies have been "scanned", i.e. information that the ORT analyzer
 * (not the ORT scanner) provides.
 */
@Serializable
data class NewScan(
    /** Name of the TrustSource module, corresponds to an ORT [Project]. May be customized by the user. */
    val module: String,
    /** SCM tag of the module, if known. */
    val tag: String = "",
    /** SCM branch of the module, if known. */
    val branch: String = "",
    /** SCM commit hash of the module, if known. */
    val commit: String = "",
    /** List of the module's dependencies. */
    val dependencies: List<Dependency>
)

@Serializable
data class Dependency(
    /** The package manager specific name of the dependency. */
    val name: String,
    /** The Package URL of the dependency. */
    val purl: String,
    /** List of the dependency's dependencies. */
    val dependencies: List<Dependency>,
    /** The dependency's description as provided by metadata. */
    val description: String = "",
    /** Dependency's homepage, may differ from or be the same as [repositoryUrl]. */
    val homepageUrl: String = "",
    /** Repository with the dependency's source code. */
    val repositoryUrl: String = "",
    /** A map of "free text" checksum algorithm names and their values. */
    val checksum: Map<String, String> = emptyMap(),
    /** The list of declared licenses as read from metadata. */
    val licenses: List<License> = emptyList(),
    /** The TrustSource [Package] for this dependency's source, if available. */
    @SerialName("package")
    val pkg: Package? = null,
    /** Indicates whether the dependency is publicly available in a package management system. */
    val private: Boolean = false
)

@Serializable
data class License(
    /** SPDX license identifier / expression. */
    val name: String,
    /** License text URL, if known. */
    val url: String = ""
)

@Serializable
data class Package(
    /** Relative path to the license file within the source code, if available. */
    val licenseFile: String = "",
    /** Download URL to the source code of the package, e.g. a Maven sources JAR. */
    val sourcesUrl: String = "",
    /** A map of "free text" checksum algorithm names and their values. */
    val sourcesChecksum: Map<String, String> = emptyMap()
)
