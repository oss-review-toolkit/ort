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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

internal val json = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/**
 * See https://doc.rust-lang.org/cargo/commands/cargo-metadata.html.
 */
@Serializable
internal data class CargoMetadata(
    /** Array of all packages in the workspace. */
    val packages: List<Package>,
    /** Array of members of the workspace. Each entry is the Package ID for the package. */
    val workspaceMembers: List<String>,
    /** The resolved dependency graph for the entire workspace. */
    val resolve: Resolve,
    /** The absolute path to the root of the workspace. */
    val workspaceRoot: String
) {
    @Serializable
    data class Package(
        /** The name of the package. */
        val name: String,
        /** The version of the package. */
        val version: String,
        /** The Package ID for referring to the package within the document. */
        val id: String,
        /** The license value from the manifest, or null. */
        val license: String? = null,
        /** The license-file value from the manifest, or null. */
        val licenseFile: String? = null,
        /** The description value from the manifest, or null. */
        val description: String? = null,
        /** The source ID of the package, an "opaque" identifier representing where a package is retrieved from. */
        val source: String? = null,
        /** Array of dependencies declared in the package's manifest. */
        val dependencies: List<Dependency> = emptyList(),
        /** Array of authors from the manifest. */
        val authors: List<String> = emptyList(),
        /** The repository value from the manifest or null if not specified. */
        val repository: String? = null,
        /** The homepage value from the manifest or null if not specified. */
        val homepage: String? = null
    )

    @Serializable
    data class Dependency(
        /** The name of the dependency. */
        val name: String,
        /** The dependency kind. "dev", "build", or null for a normal dependency. */
        val kind: String? = null
    )

    @Serializable
    data class Resolve(
        /** Array of nodes within the dependency graph. Each node is a package. */
        val nodes: List<Node>,

        /**
         * The root package of the workspace. This is null if this is a virtual workspace. Otherwise, it is the Package
         * ID of the root package.
         */
        val root: String? = null
    )

    @Serializable
    data class Node(
        /** The Package ID of this node. */
        val id: String,
        /** The dependencies of this package, an array of Package IDs. */
        val dependencies: List<String>,

        /**
         * The dependencies of this package. This is an alternative to "dependencies" which contains additional
         * information. In particular, this handles renamed dependencies.
         */
        val deps: List<Dep>
    )

    @Serializable
    data class Dep(
        /** The name of the dependency's library target. If this is a renamed dependency, this is the new name. */
        val name: String,
        /** The Package ID of the dependency. */
        val pkg: String,
        /** Array of dependency kinds. Added in Cargo 1.40. */
        val depKinds: List<DepKind>
    )

    @Serializable
    data class DepKind(
        /** The dependency kind. "dev", "build", or null for a normal dependency. */
        val kind: String? = null,
        /** The target platform for the dependency. null if not a target dependency. */
        val target: String? = null
    )
}
