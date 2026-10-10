/*
 * Copyright (C) 2024 The ORT Project Copyright Holders
 * <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import java.io.File

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.decodeFromString

private val uvToml = Toml {
    ignoreUnknownKeys = true
}

internal fun File.parseUvLockfile(): UvLockfile = uvToml.decodeFromString(readText())

@Serializable
internal data class UvLockfile(
    val version: Int,
    val revision: Int? = null,
    @SerialName("requires-python")
    val requiresPython: String? = null,
    @SerialName("package")
    val packages: List<UvPackage> = emptyList()
)

@Serializable
internal data class UvPackage(
    val name: String,
    val version: String? = null,
    val source: UvSource? = null,
    val dependencies: List<UvDependency> = emptyList(),
    @SerialName("dev-dependencies")
    val devDependencies: Map<String, List<UvDependency>> = emptyMap(),
    @SerialName("dependency-groups")
    val dependencyGroups: Map<String, List<UvDependency>> = emptyMap(),
    @SerialName("optional-dependencies")
    val optionalDependencies: Map<String, List<UvDependency>> = emptyMap(),
    val metadata: UvPackageMetadata? = null,
    val sdist: UvDistribution? = null,
    val wheels: List<UvDistribution> = emptyList()
)

@Serializable
internal data class UvPackageMetadata(
    @SerialName("requires-dist")
    val requiresDist: List<UvDependency> = emptyList(),
    @SerialName("requires-dev")
    val requiresDev: Map<String, List<UvDependency>> = emptyMap(),
    @SerialName("dependency-groups")
    val dependencyGroups: Map<String, List<UvDependency>> = emptyMap()
)

@Serializable
internal data class UvDependency(
    val name: String,
    val version: String? = null,
    val marker: String? = null,
    val extra: List<String> = emptyList(),
    val optional: Boolean = false,
    val source: UvSource? = null
)

@Serializable
internal data class UvSource(
    val registry: String? = null,
    val url: String? = null,
    val git: String? = null,
    val rev: String? = null,
    val resolved: String? = null,
    val path: String? = null,
    val editable: String? = null,
    val virtual: String? = null,
    val workspace: String? = null,
    val subdirectory: String? = null
)

@Serializable
internal data class UvDistribution(
    val url: String? = null,
    val hash: String? = null,
    val size: Long? = null
)
