/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2020-2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonIgnoreProperties(value = ["ignore_tool_versions"]) // Backwards compatibility.
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AnalyzerConfiguration(
    /**
     * Enable the analysis of projects that use version ranges to declare their dependencies. If set to true,
     * dependencies of exactly the same project might change with another scan done at a later time if any of the
     * (transitive) dependencies are declared using version ranges and a new version of such a dependency was
     * published in the meantime. If set to false, analysis of projects that use version ranges will fail. Defaults to
     * false.
     */
    val allowDynamicVersions: Boolean = false,

    /**
     * A list of the case-insensitive names of package managers that are enabled. Disabling a package manager in
     * [disabledPackageManagers] overrides enabling it here.
     */
    val enabledPackageManagers: List<String>? = null,

    /**
     * A list of the case-insensitive names of package managers that are disabled. Disabling a package manager in this
     * list overrides [enabledPackageManagers].
     */
    val disabledPackageManagers: List<String>? = null,

    /**
     * Package manager specific configurations. The key needs to match the name of the package manager class, e.g.
     * "NuGet" for the NuGet package manager.
     */
    val packageManagers: Map<String, PackageManagerConfiguration>? = null,

    /**
     * Configuration of the SW360 package curation provider.
     */
    val sw360Configuration: Sw360StorageConfiguration? = null
) {
    /**
     * A copy of [packageManagers] with case-insensitive keys.
     */
    private val packageManagersCaseInsensitive: Map<String, PackageManagerConfiguration>? =
        packageManagers?.toSortedMap(String.CASE_INSENSITIVE_ORDER)

    init {
        val duplicatePackageManagers =
            packageManagers?.keys.orEmpty() - packageManagersCaseInsensitive?.keys?.toSet().orEmpty()

        require(duplicatePackageManagers.isEmpty()) {
            "The following package managers have duplicate configuration: ${duplicatePackageManagers.joinToString()}."
        }
    }

    /**
     * Get a [PackageManagerConfiguration] from [packageManagers]. The difference to accessing the map directly is that
     * [packageManager] can be case-insensitive.
     */
    fun getPackageManagerConfiguration(packageManager: String) = packageManagersCaseInsensitive?.get(packageManager)

    /**
     * Merge this [AnalyzerConfiguration] with [other]. Values of [other] take precedence.
     */
    fun merge(other: AnalyzerConfiguration): AnalyzerConfiguration {
        val mergedPackageManagers = when {
            packageManagers == null -> other.packageManagers
            other.packageManagers == null -> packageManagers
            else -> {
                val keys = sortedSetOf(String.CASE_INSENSITIVE_ORDER).apply {
                    addAll(packageManagers.keys)
                    addAll(other.packageManagers.keys)
                }

                val result = sortedMapOf<String, PackageManagerConfiguration>(String.CASE_INSENSITIVE_ORDER)

                keys.forEach { key ->
                    val configSelf = getPackageManagerConfiguration(key)
                    val configOther = other.getPackageManagerConfiguration(key)

                    result[key] = when {
                        configSelf == null -> configOther
                        configOther == null -> configSelf
                        else -> configSelf.merge(configOther)
                    }
                }

                result
            }
        }

        return AnalyzerConfiguration(
            allowDynamicVersions = other.allowDynamicVersions,
            enabledPackageManagers = other.enabledPackageManagers ?: enabledPackageManagers,
            disabledPackageManagers = other.disabledPackageManagers ?: disabledPackageManagers,
            packageManagers = mergedPackageManagers,
            sw360Configuration = other.sw360Configuration ?: sw360Configuration
        )
    }
}
