/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

typealias PackageManagerOptions = Map<String, String>

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
     * Package manager specific configuration options. The key needs to match the name of the package manager class,
     * e.g. "NuGet" for the NuGet package manager. See the documentation of the respective class for available options.
     */
    val options: Map<String, PackageManagerOptions>? = null,

    /**
     * Configuration of the SW360 package curation provider.
     */
    val sw360Configuration: Sw360StorageConfiguration? = null
)
