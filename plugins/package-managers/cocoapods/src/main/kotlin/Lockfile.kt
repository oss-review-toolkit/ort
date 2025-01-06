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

package org.ossreviewtoolkit.plugins.packagemanagers.cocoapods

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlList
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlScalar

import org.ossreviewtoolkit.plugins.packagemanagers.cocoapods.Lockfile.CheckoutOption
import org.ossreviewtoolkit.plugins.packagemanagers.cocoapods.Lockfile.Dependency
import org.ossreviewtoolkit.plugins.packagemanagers.cocoapods.Lockfile.Pod

internal data class Lockfile(
    /** All pods that are transitively used in the project. */
    val pods: List<Pod>,

    /** Details about how to retrieve pods from external sources. */
    val checkoutOptions: Map<String, CheckoutOption>,

    /** The direct dependencies of the project. */
    val dependencies: List<Dependency>
) {
    data class Pod(
        /** The name of this pod. */
        val name: String,

        /** The resolved version of this pod. */
        val version: String? = null,

        /** The direct dependencies of this pod. */
        val dependencies: List<Pod> = emptyList()
    )

    data class CheckoutOption(
        /** The Git repository URL to check out from. */
        val git: String?,

        /** The Git commit hash to check out. */
        val commit: String?
    )

    data class Dependency(
        /** The name of this direct dependency. */
        val name: String,

        /** The version constraint for this direct dependency. */
        val versionConstraint: String?
    )
}

internal fun String.parseLockfile(): Lockfile {
    val root = Yaml.default.parseToYamlNode(this).yamlMap
    val pods = root.get<YamlList>("PODS")?.items.orEmpty().map { it.toPod() }

    val checkoutOptions = root.get<YamlMap>("CHECKOUT OPTIONS")?.entries.orEmpty().map {
        val name = it.key.content
        val node = it.value.yamlMap

        val checkoutOption = CheckoutOption(
            git = node.get<YamlScalar>(":git")?.content,
            commit = node.get<YamlScalar>(":commit")?.content
        )

        name to checkoutOption
    }.toMap()

    val dependencies = root.get<YamlList>("DEPENDENCIES")?.items.orEmpty().map { node ->
        val (name, version) = parseNameAndVersion(node.yamlScalar.content)
        Dependency(name, version)
    }

    return Lockfile(pods, checkoutOptions, dependencies)
}

private fun YamlNode.toPod(): Pod =
    when {
        this is YamlMap -> {
            val (key, value) = yamlMap.entries.entries.single()
            val (name, version) = parseNameAndVersion(key.content)

            Pod(
                name = name,
                version = version,
                dependencies = value.yamlList.items.map { it.toPod() }
            )
        }

        else -> {
            val (name, version) = parseNameAndVersion(yamlScalar.content)
            Pod(name, version)
        }
    }

private fun parseNameAndVersion(entry: String): Pair<String, String?> {
    val info = entry.split(' ', limit = 2)
    val name = info[0]

    // A version entry could look something like "(6.3.0)", "(= 2021.06.28.00-v2)", "(~> 8.15.0)", etc. Also see
    // https://guides.cocoapods.org/syntax/podfile.html#pod.
    val version = info.getOrNull(1)?.removeSurrounding("(", ")")

    return name to version
}
