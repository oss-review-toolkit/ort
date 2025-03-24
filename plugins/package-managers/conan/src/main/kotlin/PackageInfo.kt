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

internal fun parsePackageInfosV1(file: File): List<PackageInfoV1> =
    JSON.decodeFromString<List<PackageInfoV1>>(file.readText())

/**
 * A class containing the properties common to all [PackageInfo], regardless of their Conan version. Used for
 * abstracting some functions and keeping them in [Conan].
 */
@Serializable
sealed interface PackageInfo {
    val author: String?
    val revision: String?
    val url: String?
}

@Serializable
internal data class PackageInfoV1(
    override val author: String? = null,
    override val revision: String? = null,
    override val url: String? = null,
    val reference: String? = null,
    val license: List<String> = emptyList(),
    val homepage: String? = null,
    val displayName: String,
    val requires: List<String> = emptyList(),
    val buildRequires: List<String> = emptyList()
) : PackageInfo
