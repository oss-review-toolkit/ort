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

import com.fasterxml.jackson.databind.node.ObjectNode

import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.plugins.packagemanagers.cocoapods.Lockfile.CheckoutOption
import org.ossreviewtoolkit.plugins.packagemanagers.cocoapods.Lockfile.Dependency

internal data class Lockfile(
    val pods: List<Pod>,
    val checkoutOptions: Map<String, CheckoutOption>,
    val dependencies: List<Dependency>
) {
    data class Pod(
        val name: String,
        val version: String? = null,
        val dependencies: List<Pod> = emptyList()
    )

    data class CheckoutOption(
        val git: String?,
        val commit: String?
    )

    data class Dependency(
        val name: String,
        val versionConstraint: String?
    )
}

internal fun String.parseLockfile(): Lockfile {
    val root = yamlMapper.readTree(this)

    val pods = root.get("PODS").map { node ->
        when (node) {
            is ObjectNode -> {
                val (name, version) = parseNameAndVersion(node.fieldNames().asSequence().single())
                Lockfile.Pod(
                    name = name,
                    version = version,
                    dependencies = node.single().map {
                        val (depName, depVersion) = parseNameAndVersion(it.textValue())
                        Lockfile.Pod(depName, depVersion)
                    }
                )
            }

            else -> {
                val (name, version) = parseNameAndVersion(node.textValue())
                Lockfile.Pod(name, version)
            }
        }
    }

    val checkoutOptions = root.get("CHECKOUT OPTIONS")?.fields()?.asSequence()?.mapNotNull { (name, node) ->
        val checkoutOption = CheckoutOption(
            git = node[":git"]?.textValue(),
            commit = node[":commit"].textValue()
        )

        name to checkoutOption
    }.orEmpty().toMap()

    val dependencies = root.get("DEPENDENCIES").map { node ->
        val (name, version) = parseNameAndVersion(node.textValue())
        Dependency(name, version)
    }

    return Lockfile(pods, checkoutOptions, dependencies)
}

private fun parseNameAndVersion(entry: String): Pair<String, String?> {
    val info = entry.split(' ', limit = 2)
    val name = info[0]

    // A version entry could look something like "(6.3.0)", "(= 2021.06.28.00-v2)", "(~> 8.15.0)", etc. Also see
    // https://guides.cocoapods.org/syntax/podfile.html#pod.
    val version = info.getOrNull(1)?.removeSurrounding("(", ")")

    return name to version
}
