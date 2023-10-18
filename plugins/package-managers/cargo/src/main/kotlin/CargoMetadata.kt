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

import kotlinx.serialization.Serializable

/**
 * See https://doc.rust-lang.org/cargo/commands/cargo-metadata.html.
 */
@Serializable
internal data class CargoMetadata(
    val packages: List<Package>,
    val workspaceMembers: List<String>,
    val resolve: Resolve,
    val workspaceRoot: String
) {
    @Serializable
    data class Package(
        val name: String,
        val version: String,
        val id: String,
        val license: String? = null,
        val licenseFile: String? = null,
        val description: String? = null,
        val source: String? = null,
        val dependencies: List<Dependency> = emptyList(),
        val authors: List<String> = emptyList(),
        val repository: String? = null,
        val homepage: String? = null
    )

    @Serializable
    data class Dependency(
        val name: String,
        val kind: String? = null
    )

    @Serializable
    data class Resolve(
        val nodes: List<Node>,
        val root: String? = null
    )

    @Serializable
    data class Node(
        val id: String,
        val dependencies: List<String>
    )
}
