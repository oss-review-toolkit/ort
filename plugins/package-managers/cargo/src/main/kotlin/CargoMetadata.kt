/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.cargo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * See https://doc.rust-lang.org/cargo/commands/cargo-metadata.html.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class CargoMetadata(
    val packages: List<Package>,
    val workspaceMembers: List<String>,
    val resolve: Resolve,
    val workspaceRoot: String
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Package(
        val name: String,
        val version: String,
        val id: String,
        val license: String?,
        val licenseFile: String?,
        val description: String?,
        val source: String?,
        val dependencies: List<Dependency>,
        val authors: List<String>,
        val repository: String?,
        val homepage: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Dependency(
        val name: String,
        val kind: String?
    )

    data class Resolve(
        val nodes: List<Node>,
        val root: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Node(
        val id: String,
        val dependencies: List<String>
    )
}
