/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude

import com.sksamuel.hoplite.ConfigAlias

import org.ossreviewtoolkit.utils.common.zip

/**
 * The configuration model of the analyzer. This class is (de-)serialized in the following places:
 * - Deserialized from "config.yml" as part of [OrtConfiguration] (via Hoplite).
 * - (De-)Serialized as part of [org.ossreviewtoolkit.model.OrtResult] (via Jackson).
 */
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
    val enabledPackageManagers: List<String> = listOf(
        "Bazel",
        "Bower",
        "Bundler",
        "Cargo",
        "Carthage",
        "CocoaPods",
        "Composer",
        "Conan",
        "GoMod",
        "GradleInspector",
        "Maven",
        "NPM",
        "NuGet",
        "PIP",
        "Pipenv",
        "PNPM",
        "Poetry",
        "Pub",
        "SBT",
        "SpdxDocumentFile",
        "Stack",
        "SwiftPM",
        "Tycho",
        "Unmanaged",
        "Yarn",
        "Yarn2"
    ),

    /**
     * A list of the case-insensitive names of package managers that are disabled. Disabling a package manager in this
     * list overrides [enabledPackageManagers].
     */
    val disabledPackageManagers: List<String>? = null,

    /**
     * Package manager specific configurations. The key needs to match the name of the package manager class, e.g.
     * "NuGet" for the NuGet package manager.
     */
    @ConfigAlias("analyzers")
    @JsonAlias("analyzers")
    val packageManagers: Map<String, PackageManagerConfiguration>? = null,

    /**
     * A flag to control whether excluded scopes and paths should be skipped during the analysis.
     */
    val skipExcluded: Boolean = false
) {
    /**
     * A copy of [packageManagers] with case-insensitive keys.
     */
    private val packageManagersCaseInsensitive = packageManagers?.let {
        sortedMapOf<String, PackageManagerConfiguration>(String.CASE_INSENSITIVE_ORDER).zip(it) { a, b ->
            a.merge(b)
        }
    }

    /**
     * Get a [PackageManagerConfiguration] from [packageManagers]. The difference to accessing the map directly is that
     * [packageManager] can be case-insensitive.
     */
    fun getPackageManagerConfiguration(packageManager: String) = packageManagersCaseInsensitive?.get(packageManager)

    /**
     * Merge this [AnalyzerConfiguration] with [override]. Values of [override] take precedence.
     */
    fun merge(override: RepositoryAnalyzerConfiguration): AnalyzerConfiguration {
        val mergedPackageManagers = when {
            packageManagers == null -> override.packageManagers
            override.packageManagers == null -> packageManagers
            else -> {
                val keys = sortedSetOf(String.CASE_INSENSITIVE_ORDER).apply {
                    addAll(packageManagers.keys)
                    addAll(override.packageManagers.keys)
                }

                val result = sortedMapOf<String, PackageManagerConfiguration>(String.CASE_INSENSITIVE_ORDER)

                keys.forEach { key ->
                    val configSelf = getPackageManagerConfiguration(key)
                    val configOther = override.getPackageManagerConfiguration(key)

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
            allowDynamicVersions = override.allowDynamicVersions ?: allowDynamicVersions,
            enabledPackageManagers = override.enabledPackageManagers ?: enabledPackageManagers,
            disabledPackageManagers = override.disabledPackageManagers ?: disabledPackageManagers,
            packageManagers = mergedPackageManagers,
            skipExcluded = override.skipExcluded ?: skipExcluded
        )
    }

    /**
     * Return a copy of this [AnalyzerConfiguration] "patched" with [name]-specific options to contain the given [key]
     * and [value] pair, overriding any existing entry.
     */
    fun withPackageManagerOption(name: String, key: String, value: String): AnalyzerConfiguration {
        val override = mapOf(name to PackageManagerConfiguration(options = mapOf(key to value)))
        return copy(packageManagers = packageManagersCaseInsensitive.orEmpty().zip(override) { a, b -> a.merge(b) })
    }
}
