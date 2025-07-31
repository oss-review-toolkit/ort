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

import com.fasterxml.jackson.annotation.JsonInclude

import org.ossreviewtoolkit.model.utils.CurationsFilter
import org.ossreviewtoolkit.model.utils.ExcludesFilter
import org.ossreviewtoolkit.model.utils.IncludesFilter
import org.ossreviewtoolkit.model.utils.LicenseChoicesFilter
import org.ossreviewtoolkit.model.utils.ResolutionsFilter
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME

/**
 * A distribution-specific configuration for ORT which is usually stored in [ORT_REPO_CONFIG_FILENAME] at the root of a
 * repository. It will be included in the analyzer result and can be further processed by the other tools.
 */
data class RepositoryConfiguration(
    /**
     * The configuration for the analyzer. Values in this configuration take precedence over global configuration.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val analyzer: RepositoryAnalyzerConfiguration? = null,

    /**
     * Defines which parts of the repository will be excluded. Note that excluded parts will still be analyzed and
     * scanned, but related errors will be marked as resolved in the reporter output.
     */
    @JsonInclude(JsonInclude.Include.CUSTOM, valueFilter = ExcludesFilter::class)
    val excludes: Excludes = Excludes(),

    /**
     * Defines which parts of the repository will be included.
     */
    @JsonInclude(JsonInclude.Include.CUSTOM, valueFilter = IncludesFilter::class)
    val includes: Includes = Includes.EMPTY,

    /**
     * Defines resolutions for issues with this repository.
     */
    @JsonInclude(JsonInclude.Include.CUSTOM, valueFilter = ResolutionsFilter::class)
    val resolutions: Resolutions = Resolutions(),

    /**
     * Defines curations for packages used as dependencies by projects in this repository, or curations for license
     * findings in the source code of a project in this repository.
     */
    @JsonInclude(JsonInclude.Include.CUSTOM, valueFilter = CurationsFilter::class)
    val curations: Curations = Curations(),

    /**
     * Defines configurations for packages used as dependencies by projects in this repository.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val packageConfigurations: List<PackageConfiguration> = emptyList(),

    /**
     * Defines license choices within this repository.
     */
    @JsonInclude(JsonInclude.Include.CUSTOM, valueFilter = LicenseChoicesFilter::class)
    val licenseChoices: LicenseChoices = LicenseChoices(),

    /**
     * Defines snippet choices for projects in this repository.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val snippetChoices: List<SnippetChoices> = emptyList()
)
