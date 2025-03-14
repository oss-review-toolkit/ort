/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.conan

import java.io.File

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

private val JSON = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

internal fun parsePackageInfosV1(file: File): List<PackageInfo> =
    JSON.decodeFromString<List<PackageInfoRaw>>(file.readText()).map { it.toPackageInfo() }

/**
 * A class containing the properties common to all [PackageInfo], regardless of their Conan version. Used for
 * abstracting some functions and keeping them in [Conan].
 */
@Serializable
sealed interface Info {
    val author: String?
    val revision: String?
    val url: String?
}

@Serializable
internal data class PackageInfo(
    override val author: String?,
    override val revision: String?,
    override val url: String?,
    val reference: String? = null,
    val license: List<String> = emptyList(),
    val homepage: String? = null,
    val displayName: String,
    val requires: List<String> = emptyList(),
    val buildRequires: List<String> = emptyList()
) : Info

@Serializable
private data class PackageInfoRaw(
    val reference: String? = null,
    val author: String? = null,
    val license: List<String> = emptyList(),
    val homepage: String? = null,
    val revision: String? = null,
    val url: String? = null,
    val displayName: String,
    val requires: List<String> = emptyList(),
    val buildRequires: List<String> = emptyList()
) {
    fun toPackageInfo(): PackageInfo {
        return PackageInfo(
            author = author,
            revision = revision,
            url = url,
            reference = reference,
            license = license,
            homepage = homepage,
            displayName = displayName,
            requires = requires,
            buildRequires = buildRequires
        )
    }
}
