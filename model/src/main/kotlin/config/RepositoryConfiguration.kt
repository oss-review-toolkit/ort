/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME

/**
 * A project specific configuration for ORT which is usually stored in [ORT_REPO_CONFIG_FILENAME] at the root of a
 * repository. It will be included in the analyzer result and can be further processed by the other tools.
 */
data class RepositoryConfiguration(
    /**
     * The configuration for the analyzer. Values in this configuration take precedence over global configuration.
     */
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    val analyzer: AnalyzerConfiguration? = null,

    /**
     * Defines which parts of the repository will be excluded. Note that excluded parts will still be analyzed and
     * scanned, but related errors will be marked as resolved in the reporter output.
     */
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ExcludesFilter::class)
    val excludes: Excludes = Excludes(),

    /**
     * Defines resolutions for issues with this repository.
     */
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResolutionsFilter::class)
    val resolutions: Resolutions = Resolutions(),

    /**
     * Defines curations for artifacts contained in this repository.
     */
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = CurationsFilter::class)
    val curations: Curations = Curations(),

    /**
     * Defines configurations for this repository.
     */
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    val packageConfigurations: List<PackageConfiguration> = emptyList(),

    /**
     * Defines license choices within this repository.
     */
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LicenseChoiceFilter::class)
    val licenseChoices: LicenseChoices = LicenseChoices()
)

@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist") // The class is not supposed to be used with hashing.
private class ExcludesFilter {
    override fun equals(other: Any?): Boolean =
        if (other is Excludes) other.paths.isEmpty() && other.scopes.isEmpty() else false
}

@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist") // The class is not supposed to be used with hashing.
private class ResolutionsFilter {
    override fun equals(other: Any?): Boolean =
        other is Resolutions &&
                other.issues.isEmpty() &&
                other.ruleViolations.isEmpty() &&
                other.vulnerabilities.isEmpty()
}

@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist") // The class is not supposed to be used with hashing.
private class CurationsFilter {
    override fun equals(other: Any?): Boolean =
        other is Curations && other.licenseFindings.isEmpty() && other.packages.isEmpty()
}

@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist") // The class is not supposed to be used with hashing.
private class LicenseChoiceFilter {
    override fun equals(other: Any?): Boolean =
        other is LicenseChoices && other.isEmpty()
}
