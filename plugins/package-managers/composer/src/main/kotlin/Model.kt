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

package org.ossreviewtoolkit.plugins.packagemanagers.composer

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue

import org.ossreviewtoolkit.model.jsonMapper

@JsonIgnoreProperties(ignoreUnknown = true)
data class Lockfile(
    val packages: List<PackageInfo> = emptyList(),
    @JsonProperty("packages-dev")
    val packagesDev: List<PackageInfo> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PackageInfo(
    val name: String?,
    // See https://getcomposer.org/doc/04-schema.md#version.
    val version: String?,
    val homepage: String?,
    val description: String?,
    val source: Source? = null,
    val authors: List<Author> = emptyList(),
    val license: List<String> = emptyList(),
    val provide: Map<String, String> = emptyMap(),
    val replace: Map<String, String> = emptyMap(),
    val require: Map<String, String> = emptyMap(),
    @JsonProperty("require-dev")
    val requireDev: Map<String, String> = emptyMap(),
    val dist: Dist? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Author(
        val name: String? = null,
        val email: String? = null,
        val homepage: String? = null,
        val role: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Source(
        val type: String? = null,
        val url: String? = null,
        val reference: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Dist(
        val type: String?,
        val url: String?,
        val reference: String?,
        val shasum: String?
    )
}

fun parseLockfile(json: String): Lockfile = jsonMapper.readValue<Lockfile>(json)

fun parsePackageInfo(json: String): PackageInfo = jsonMapper.readValue<PackageInfo>(json)
