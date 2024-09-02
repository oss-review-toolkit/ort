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

package org.ossreviewtoolkit.utils.config

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.utils.ResolutionProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider

/**
 * Replace the package configurations in [OrtResult.resolvedConfiguration] with the ones obtained from
 * [packageConfigurationProvider].
 */
fun OrtResult.setPackageConfigurations(packageConfigurationProvider: PackageConfigurationProvider): OrtResult {
    val packageConfigurations = ConfigurationResolver.resolvePackageConfigurations(
        identifiers = getUncuratedPackages().mapTo(mutableSetOf()) { it.id },
        scanResultProvider = { id -> getScanResultsForId(id) },
        packageConfigurationProvider = packageConfigurationProvider
    )

    return copy(resolvedConfiguration = resolvedConfiguration.copy(packageConfigurations = packageConfigurations))
}

/**
 * Replace the package curations in [OrtResult.resolvedConfiguration] with the ones obtained from
 * [packageCurationProviders]. The [packageCurationProviders] must be ordered highest-priority-first.
 */
fun OrtResult.setPackageCurations(packageCurationProviders: List<Pair<String, PackageCurationProvider>>): OrtResult {
    val packageCurations =
        ConfigurationResolver.resolvePackageCurations(getUncuratedPackages(), packageCurationProviders)

    return copy(resolvedConfiguration = resolvedConfiguration.copy(packageCurations = packageCurations))
}

/**
 * Replace the resolutions in [OrtResult.resolvedConfiguration] with the ones obtained from [resolutionProvider].
 */
fun OrtResult.setResolutions(resolutionProvider: ResolutionProvider): OrtResult {
    val resolutions = ConfigurationResolver.resolveResolutions(
        issues = getIssues().values.flatten(),
        ruleViolations = getRuleViolations(),
        vulnerabilities = getVulnerabilities().values.flatten(),
        resolutionProvider = resolutionProvider
    )

    return copy(resolvedConfiguration = resolvedConfiguration.copy(resolutions = resolutions))
}
