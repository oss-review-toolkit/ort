/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.utils.FindingCurationMatcher
import org.ossreviewtoolkit.model.utils.PathLicenseMatcher
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.dir.DirPackageConfigurationProvider
import org.ossreviewtoolkit.scanner.ScanStorages
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.toExpression

internal class GetPackageLicensesCommand : OrtHelperCommand(
    help = "Shows the root license and the detected license for a package denoted by the given package identifier."
) {
    private val configFile by option(
        "--config",
        help = "The path to the ORT configuration file that configures the scan results storage."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_CONFIG_FILENAME))

    private val configArguments by option(
        "-P",
        help = "Override a key-value pair in the configuration file. For example: " +
            "-P ort.scanner.storages.postgres.connection.schema=testSchema"
    ).associate()

    private val packageId by option(
        "--package-id",
        help = "The target package for which the licenses shall be listed."
    ).convert { Identifier(it) }
        .required()

    private val packageConfigurationsDir by option(
        "--package-configurations-dir",
        help = "A directory that is searched recursively for package configuration files. Each file must only " +
            "contain a single package configuration."
    ).file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.expandTilde() }

    override fun run() {
        val scanResults = getStoredScanResults(Package.EMPTY.copy(id = packageId))
        val packageConfigurationProvider = DirPackageConfigurationProvider(packageConfigurationsDir)

        val result = scanResults.firstOrNull()?.let { scanResult ->
            val packageConfigurations =
                packageConfigurationProvider.getPackageConfigurations(packageId, scanResult.provenance)

            val licenseFindingCurations = packageConfigurations.flatMap { it.licenseFindingCurations }
            val pathExcludes = packageConfigurations.flatMap { it.pathExcludes }

            val nonExcludedLicenseFindings = scanResult.summary.licenseFindings.filter { licenseFinding ->
                pathExcludes.none { it.matches(licenseFinding.location.path) }
            }

            val curatedFindings = FindingCurationMatcher()
                .applyAll(nonExcludedLicenseFindings, licenseFindingCurations)
                .mapNotNull { it.curatedFinding }

            val detectedLicense = curatedFindings.toSpdxExpression()

            val rootLicense = PathLicenseMatcher().getApplicableLicenseFindingsForDirectories(
                licenseFindings = curatedFindings,
                directories = listOf("") // TODO: use the proper VCS path.
            ).values.flatten().toSpdxExpression()

            Result(detectedLicense, rootLicense)
        } ?: Result(SpdxConstants.NOASSERTION, SpdxConstants.NOASSERTION)

        println(result.toYaml())
    }

    private fun getStoredScanResults(pkg: Package): List<ScanResult> {
        val ortConfiguration = OrtConfiguration.load(configArguments, configFile)
        val scanStorages = ScanStorages.createFromConfig(ortConfiguration.scanner)
        return runCatching { scanStorages.read(pkg) }.getOrDefault(emptyList())
    }
}

private fun Collection<LicenseFinding>.toSpdxExpression(): String =
    map { it.license }.toExpression()?.sorted()?.toString() ?: SpdxConstants.NONE

private data class Result(
    val detectedLicense: String,
    val rootLicense: String
) {
    fun toYaml(): String = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
}
