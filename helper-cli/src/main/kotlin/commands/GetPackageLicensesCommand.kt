/*
 * Copyright (C) 2021 HERE Europe B.V.
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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.PackageConfigurationOption
import org.ossreviewtoolkit.helper.common.createProvider
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.utils.FindingCurationMatcher
import org.ossreviewtoolkit.model.utils.RootLicenseMatcher
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.core.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.core.ortConfigDirectory
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

class GetPackageLicensesCommand : CliktCommand(
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
                "-P ort.scanner.storages.postgres.schema=testSchema"
    ).associate()

    private val packageId by option(
        "--package-id",
        help = "The target package for which the licenses shall be listed."
    ).convert { Identifier(it) }
        .required()

    private val packageConfigurationOption by mutuallyExclusiveOptions(
        option(
            "--package-configuration-dir",
            help = "The directory containing the package configuration files to read as input. It is searched " +
                    "recursively."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
            .convert { PackageConfigurationOption.Dir(it) },
        option(
            "--package-configuration-file",
            help = "The file containing the package configurations to read as input."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
            .convert { PackageConfigurationOption.File(it) }
    ).single()

    override fun run() {
        val scanResults = getScanResultStorage().read(packageId).getOrDefault(emptyList())
        val packageConfigurationProvider = packageConfigurationOption.createProvider()

        val result = scanResults.firstOrNull()?.let { scanResult ->
            val packageConfiguration = packageConfigurationProvider.getPackageConfiguration(
                packageId, scanResult.provenance
            )

            val licenseFindingCurations = packageConfiguration?.licenseFindingCurations.orEmpty()
            val pathExcludes = packageConfiguration?.pathExcludes.orEmpty()

            val nonExcludedLicenseFindings = scanResult.summary.licenseFindings.filter { licenseFinding ->
                pathExcludes.none { it.matches(licenseFinding.location.path) }
            }

            val curatedFindings = FindingCurationMatcher()
                .applyAll(nonExcludedLicenseFindings, licenseFindingCurations)
                .mapNotNull { it.curatedFinding }

            val detectedLicense = curatedFindings.toSpdxExpression()

            val rootLicense = RootLicenseMatcher().getApplicableRootLicenseFindingsForDirectories(
                licenseFindings = curatedFindings,
                directories = listOf("") // TODO: use the proper VCS path.
            ).values.flatten().toSpdxExpression()

            Result(detectedLicense, rootLicense)
        } ?: Result(SpdxConstants.NOASSERTION, SpdxConstants.NOASSERTION)

        println(result.toYaml())
    }

    private fun getScanResultStorage(): ScanResultsStorage {
        val ortConfiguration = OrtConfiguration.load(configArguments, configFile)
        ScanResultsStorage.configure(ortConfiguration.scanner)
        return ScanResultsStorage.storage
    }
}

private fun Collection<LicenseFinding>.toSpdxExpression(): String =
    if (isEmpty()) {
        SpdxConstants.NONE
    } else {
        asSequence().map { it.license }.distinct().reduce(SpdxExpression::and).sort().toString()
    }

private data class Result(
    val detectedLicense: String,
    val rootLicense: String
) {
    fun toYaml(): String = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
}
