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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude

import org.ossreviewtoolkit.utils.common.zip

/**
 * [AnalyzerConfiguration] options that can be configured in the [RepositoryConfiguration].
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RepositoryAnalyzerConfiguration(
    /**
     * Enable the analysis of projects that use version ranges to declare their dependencies. If set to true,
     * dependencies of exactly the same project might change with another scan done at a later time if any of the
     * (transitive) dependencies are declared using version ranges and a new version of such a dependency was
     * published in the meantime. If set to false, analysis of projects that use version ranges will fail.
     *
     * If set to null, the global configuration from [AnalyzerConfiguration.allowDynamicVersions] will be used.
     */
    val allowDynamicVersions: Boolean? = null,

    /**
     * A list of the case-insensitive names of package managers that are enabled. Disabling a package manager in
     * [disabledPackageManagers] overrides enabling it here.
     *
     * If set to null, the global configuration from [AnalyzerConfiguration.enabledPackageManagers] will be used.
     */
    val enabledPackageManagers: List<String>? = null,

    /**
     * A list of the case-insensitive names of package managers that are disabled. Disabling a package manager in this
     * list overrides [enabledPackageManagers].
     *
     * If set to null, the global configuration from [AnalyzerConfiguration.disabledPackageManagers] will be used.
     */
    val disabledPackageManagers: List<String>? = null,

    /**
     * Package manager specific configurations. The key needs to match the name of the package manager class, e.g.
     * "NuGet" for the NuGet package manager.
     *
     * If set to null, the global configuration from [AnalyzerConfiguration.packageManagers] will be used.
     */
    @JsonAlias("analyzers")
    val packageManagers: Map<String, PackageManagerConfiguration>? = null,

    /**
     * A flag to control whether excluded scopes and paths should be skipped during the analysis.
     *
     * If set to null, the global configuration from [AnalyzerConfiguration.skipExcluded] will be used.
     */
    val skipExcluded: Boolean? = null
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
}
