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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence

private val JSON = Json { ignoreUnknownKeys = true }

internal fun parsePackageInfos(info: String): List<PackageInfo> =
    info.byteInputStream().use { JSON.decodeToSequence<PackageInfo>(it) }.toList()

@Serializable
internal data class PackageInfo(
    val value: String,
    val children: Children
) {
    @Serializable
    data class Children(
        @SerialName("Version")
        val version: String,
        @SerialName("Manifest")
        val manifest: Manifest,
        @SerialName("Dependencies")
        val dependencies: List<Dependency> = emptyList()
    )

    @Serializable
    data class Manifest(
        @SerialName("License")
        val license: String? = null,
        @SerialName("Homepage")
        val homepage: String? = null
    )

    @Serializable
    data class Dependency(
        val descriptor: String,
        val locator: String
    )
}
