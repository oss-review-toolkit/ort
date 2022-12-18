/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.reporter.reporters

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import java.io.File

/**
 * A [Reporter] that adds configuration to the ORT result file. This result can be used as input for third-party tools
 * like the [ORT Workbench](https://github.com/oss-review-toolkit/ort-workbench) to make sure that it contains exactly
 * the same information that as other reports created alongside. For example, if a package configuration provider
 * provides a different set of configurations for the packages contained in the result file at a later time, the
 * third-party tool could not reproduce the results from the time the other reports were created anymore.
 *
 * This reporter supports the following options:
 * - *output.file.formats*: The list of [FileFormat]s to generate, defaults to [FileFormat.JSON].
 */
class BakedResultReporter : Reporter {
    override val name = "BakedResult"

    override fun generateReport(input: ReporterInput, outputDir: File, options: Map<String, String>): List<File> {
        // Find package configurations matching packages.
        val packageConfigurations = findPackageConfigurations()

        // Find resolutions matching issues.
        val issueResolutions = findIssueResolutions()

        // Find resolutions matching vulnerabilities.
        val vulnerabilityResolutions = findVulnerabilityResolutions()

        // Find resolutions matching rule violations.
        val ruleViolationResolutions = findRuleViolationResolutions()

        val repositoryConfig = mergeConfig(
            input.ortResult.repository.config,
            packageConfigurations,
            issueResolutions,
            vulnerabilityResolutions,
            ruleViolationResolutions
        )

        val result = input.ortResult.copy(
            repository = input.ortResult.repository.copy(config = repositoryConfig)
        )

        // TODO: Write result to file.
    }

    private fun findPackageConfigurations(input: ReporterInput): List<PackageConfiguration> {
        input.ortResult.getPackages().forEach { pkg ->
            input.licenseInfoResolver.resolveLicenseInfo(pkg.id).licenseInfo.detectedLicenseInfo.findings.
        }
    }

    private fun findIssueResolutions(): List<IssueResolution> {

    }

    private fun findVulnerabilityResolutions(): List<VulnerabilityResolution> {

    }

    private fun findRuleViolationResolutions(): List<RuleViolationResolution> {
    }

    private fun mergeConfig(
        config: RepositoryConfiguration,
        packageConfigurations: List<PackageConfiguration>,
        issueResolutions: List<IssueResolution>,
        vulnerabilityResolutions: List<VulnerabilityResolution>,
        ruleViolationResolutions: List<RuleViolationResolution>
    ): RepositoryConfiguration {
        // TODO: Include global analyzer configuration.
        config.copy(
            resolutions = config.resolutions.merge(
                Resolutions(issueResolutions, ruleViolationResolutions, vulnerabilityResolutions)
            ),
            packageConfigurations = (config.packageConfigurations + packageConfigurations).distinct()
        )
    }
}
