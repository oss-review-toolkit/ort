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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValues

import org.ossreviewtoolkit.model.jsonMapper

internal fun parsePackageInfos(info: String): List<PackageInfo> =
    jsonMapper.createParser(info).use { parser ->
        jsonMapper.readValues<PackageInfo>(parser).readAll()
    }

internal data class PackageInfo(
    val value: String,
    val children: Children
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Children(
        @JsonProperty("Version")
        val version: String,
        @JsonProperty("Manifest")
        val manifest: Manifest,
        @JsonProperty("Dependencies")
        val dependencies: List<Dependency> = emptyList()
    )

    data class Manifest(
        @JsonProperty("License")
        val license: String? = null,
        @JsonProperty("Homepage")
        val homepage: String? = null
    )

    data class Dependency(
        val descriptor: String,
        val locator: String
    )
}
