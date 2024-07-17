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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File

import org.ossreviewtoolkit.model.jsonMapper

private val mapper = jsonMapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

internal fun parsePackageInfos(file: File): List<PackageInfo> = mapper.readValue<List<PackageInfo>>(file)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PackageInfo(
    val reference: String? = null,
    val author: String? = null,
    val license: List<String> = emptyList(),
    val homepage: String? = null,
    val revision: String? = null,
    val url: String? = null,
    val displayName: String,
    val requires: List<String> = emptyList(),
    val buildRequires: List<String> = emptyList()
)
