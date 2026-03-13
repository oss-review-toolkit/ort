/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.ortproject

import com.charleskorn.kaml.Yaml

import java.io.File
import java.io.IOException

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
internal data class OrtProject(
    val projectName: String? = null,
    val declaredLicenses: Set<String> = emptySet(),
    val description: String? = null,
    val homepageUrl: String? = null,
    val authors: Set<String> = emptySet(),
    val dependencies: List<Dependency>
) {
    @Serializable
    data class Dependency(
        val id: String? = null,
        val purl: String? = null,
        val description: String? = null,
        val vcs: Vcs? = null,
        val sourceArtifact: SourceArtifact? = null,
        val declaredLicenses: Set<String> = emptySet(),
        val homepageUrl: String? = null,
        val labels: Map<String, String> = emptyMap(),
        val authors: Set<String> = emptySet(),
        val scopes: Set<String>? = null,
        val isModified: Boolean? = null,
        val isMetadataOnly: Boolean? = null
    )

    @Serializable
    data class Vcs(
        val type: String,
        val url: String,
        val revision: String,
        val path: String = ""
    )

    @Serializable
    data class SourceArtifact(
        val url: String,
        val hash: Hash
    )

    @Serializable
    data class Hash(
        val value: String,
        val algorithm: String
    )
}

internal fun File.parseOrtProject(): OrtProject =
    runCatching {
        when (extension) {
            "json" -> Json.decodeFromString<OrtProject>(readText())
            "yml", "yaml" -> Yaml.default.decodeFromString<OrtProject>(readText())
            else -> error("Unknown file extension: '$extension'.")
        }
    }.getOrElse { cause ->
        throw IOException("Could not parse ORT project file at '$absolutePath'.", cause)
    }
