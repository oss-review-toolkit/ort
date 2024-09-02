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

import kotlin.time.measureTimedValue

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.ResolvedPackageCurations
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.utils.ResolutionProvider
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider

object ConfigurationResolver {
    /**
     * Resolved the [PackageConfiguration]s that match the [scan results][scanResultProvider] for the provided
     * [identifiers].
     */
    fun resolvePackageConfigurations(
        identifiers: Set<Identifier>,
        scanResultProvider: (id: Identifier) -> List<ScanResult>,
        packageConfigurationProvider: PackageConfigurationProvider
    ): List<PackageConfiguration> =
        identifiers.flatMap { id ->
            scanResultProvider(id).flatMap { scanResult ->
                packageConfigurationProvider.getPackageConfigurations(id, scanResult.provenance)
            }
        }.distinct()

    /**
     * Return the resolved [PackageCuration]s for the given [packages]. The [curationProviders] must be ordered
     * highest-priority-first.
     */
    fun resolvePackageCurations(
        packages: Collection<Package>,
        curationProviders: List<Pair<String, PackageCurationProvider>>
    ): List<ResolvedPackageCurations> {
        val packageCurations = mutableMapOf<String, List<PackageCuration>>()

        curationProviders.forEach { (id, curationProvider) ->
            val (curations, duration) = measureTimedValue {
                curationProvider.getCurationsFor(packages)
            }

            // While every provider is supposed to only return applicable curations, filter to be on the safe side and
            // only embed applicable curations in the ORT result.
            val (applicableCurations, nonApplicableCurations) = curations.partition { curation ->
                packages.any { pkg -> curation.isApplicable(pkg.id) }
            }

            if (nonApplicableCurations.isNotEmpty()) {
                logger.warn {
                    "The provider '$id' returned the following non-applicable curations: " +
                        "${nonApplicableCurations.joinToString()}."
                }
            }

            packageCurations[id] = applicableCurations

            logger.info { "Getting ${curations.size} package curation(s) from provider '$id' took $duration." }
        }

        return packageCurations.map { (providerId, curations) ->
            ResolvedPackageCurations(
                provider = ResolvedPackageCurations.Provider(providerId),
                curations = curations
            )
        }
    }

    fun resolveResolutions(
        issues: List<Issue>,
        ruleViolations: List<RuleViolation>,
        vulnerabilities: List<Vulnerability>,
        resolutionProvider: ResolutionProvider
    ) = Resolutions(
        issues = issues.flatMap { resolutionProvider.getResolutionsFor(it) }.distinct(),
        ruleViolations = ruleViolations.flatMap { resolutionProvider.getResolutionsFor(it) }.distinct(),
        vulnerabilities = vulnerabilities.flatMap { resolutionProvider.getResolutionsFor(it) }.distinct()
    )
}
