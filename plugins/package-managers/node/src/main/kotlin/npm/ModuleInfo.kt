/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.npm

import java.util.LinkedList

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.plugins.packagemanagers.node.Scope

private val JSON = Json { ignoreUnknownKeys = true }

internal fun parseNpmList(json: String): ModuleInfo = JSON.decodeFromString(json)

/**
 * Module information for installed NPM packages.
 */
@Serializable
internal data class ModuleInfo(
    /** The name of the package. */
    val name: String? = null,

    /** The resolved version of the package. */
    val version: String? = null,

    /** The path to the directory where the package is installed. */
    val path: String? = null,

    /** The ID of the NPM package. */
    @SerialName("_id")
    val id: String? = null,

    /** The dependencies of the package with unresolved versions. */
    @SerialName("_dependencies")
    val dependencyConstraints: Map<String, String> = emptyMap(),

    /** The dependencies of the package. */
    val dependencies: Map<String, ModuleInfo> = emptyMap(),

    /** A flag to indicate whether this is an optional dependency. */
    val optional: Boolean = false,

    /** A flag to indicate whether this is a development dependency. */
    val dev: Boolean = false,

    /** The URI from where the package was resolved. Starts with "file:" for local packages. */
    val resolved: String? = null
)

internal fun ModuleInfo.getAllPackageNodeModuleIds(scopes: Set<Scope>): Set<String> =
    buildSet {
        val queue = scopes.flatMapTo(LinkedList()) { getScopeDependencies(it) }

        while (queue.isNotEmpty()) {
            val info = queue.removeFirst()

            @Suppress("ComplexCondition")
            if (!info.isProject && info.isInstalled && !info.name.isNullOrBlank() && !info.version.isNullOrBlank()) {
                add("${info.name}@${info.version}")
            }

            scopes.flatMapTo(queue) { info.getScopeDependencies(it) }
        }
    }

internal fun ModuleInfo.getScopeDependencies(scope: Scope) =
    when (scope) {
        Scope.DEPENDENCIES -> dependencies.values.filter { !it.dev }
        Scope.DEV_DEPENDENCIES -> dependencies.values.filter { it.dev && !it.optional }
    }

internal fun ModuleInfo.undoDeduplication(): ModuleInfo {
    val replacements = getNonDeduplicatedModuleInfosForId()

    fun ModuleInfo.undoDeduplicationRec(ancestorsIds: Set<String> = emptySet()): ModuleInfo {
        val dependencyAncestorIds = ancestorsIds + setOfNotNull(id)
        val replacement = replacements[id] ?: this

        return replacement.copy(
            dependencies = replacement.dependencies
                .filter { it.value.id !in dependencyAncestorIds } // break cycles.
                .mapValues { it.value.undoDeduplicationRec(dependencyAncestorIds) }
        )
    }

    return undoDeduplicationRec()
}

internal fun ModuleInfo.filterInstalled(): ModuleInfo =
    copy(dependencies = dependencies.filter { it.value.isInstalled })

private fun ModuleInfo.getNonDeduplicatedModuleInfosForId(): Map<String, ModuleInfo> {
    val queue = LinkedList<ModuleInfo>().apply { add(this@getNonDeduplicatedModuleInfosForId) }
    val result = mutableMapOf<String, ModuleInfo>()

    while (queue.isNotEmpty()) {
        val info = queue.removeFirst()

        if (info.id != null && info.dependencyConstraints.keys.subtract(info.dependencies.keys).isEmpty()) {
            result[info.id] = info
        }

        queue += info.dependencies.values
    }

    return result
}
