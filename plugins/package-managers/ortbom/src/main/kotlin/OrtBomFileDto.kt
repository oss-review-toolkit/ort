/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.ortbom

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import org.ossreviewtoolkit.utils.spdx.SpdxExpression

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class OrtBomProjectDto(
    val projectName: String?,
    val authors: Set<String>? = emptySet(),
    val vcs: VcsDto?,
    val homepageUrl: String?,
    val dependencies: List<DependencyDto> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DependencyDto(
    val purl: String,
    val vcs: VcsDto?,
    val sourceArtifact: SourceArtifactDto?,
    val declaredLicenses: Set<String> = emptySet(),
    val concludedLicense: SpdxExpression?,
    val description: String?,
    val homepageUrl: String?,
    val isExcluded: Boolean = false,
    val labels: Map<String, String> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SourceArtifactDto(
    val url: String?,
    val hash: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class VcsDto(
    val type: String?,
    val url: String?,
    val revision: String?,
    val path: String?
)
