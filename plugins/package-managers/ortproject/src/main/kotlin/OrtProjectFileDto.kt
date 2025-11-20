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

import kotlinx.serialization.Serializable

@Serializable
internal data class OrtProjectFileDto(
    val projectName: String? = null,
    val declaredLicenses: Set<String> = emptySet(),
    val description: String? = null,
    val homepageUrl: String? = null,
    val authors: Set<String> = emptySet(),
    val dependencies: List<DependencyDto>
)

@Serializable
internal data class DependencyDto(
    val id: String? = null,
    val purl: String? = null,
    val description: String? = null,
    val vcs: VcsDto? = null,
    val sourceArtifact: SourceArtifactDto? = null,
    val declaredLicenses: Set<String> = emptySet(),
    val homepageUrl: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val authors: Set<String> = emptySet(),
    val scopes: Set<String>? = null,
    val isModified: Boolean? = null,
    val isMetadataOnly: Boolean? = null
)

@Serializable
internal data class VcsDto(
    val type: String,
    val url: String,
    val revision: String,
    val path: String = ""
)

@Serializable
internal data class SourceArtifactDto(
    val url: String,
    val hash: HashDto
)

@Serializable
internal data class HashDto(
    val value: String,
    val algorithm: String
)
