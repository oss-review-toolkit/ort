/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.helper.commands.packageconfig

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.getScanResultFor
import org.ossreviewtoolkit.helper.common.readOrtResult
import org.ossreviewtoolkit.helper.common.write
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.FindingCurationMatcher
import org.ossreviewtoolkit.utils.common.expandTilde

internal class RemoveEntriesCommand : CliktCommand(
    help = "Removes all path excludes and license finding curations which do not match any files or license findings."
) {
    private val packageConfigurationFile by option(
        "--package-configuration-file",
        help = "The package configuration."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = true, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val ortFile by option(
        "--ort-file",
        help = "The ORT result file to read as input which should contain a scan result to which the given " +
                "package configuration applies to."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val findingsMatcher = FindingCurationMatcher()

    override fun run() {
        val packageConfiguration = packageConfigurationFile.readValue<PackageConfiguration>()
        val ortResult = readOrtResult(ortFile)
        val scanResult = ortResult.getScanResultFor(packageConfiguration)

        if (scanResult == null) {
            println("No scan result found for the given provenance matching id '${packageConfiguration.id}'.")
            return
        }

        val allFiles = scanResult.getAllFiles()

        val pathExcludes = packageConfiguration.pathExcludes.filter { pathExclude ->
            allFiles.any { pathExclude.matches(it) }
        }

        val licenseFindingCurations = packageConfiguration.licenseFindingCurations.filter { curation ->
            scanResult.summary.licenseFindings.any { finding -> findingsMatcher.matches(finding, curation) }
        }

        packageConfiguration.copy(
            pathExcludes = pathExcludes,
            licenseFindingCurations = licenseFindingCurations
        ).write(packageConfigurationFile)

        buildString {
            val removedPathExcludes = packageConfiguration.pathExcludes.size - pathExcludes.size
            val removedLicenseFindingCurations = packageConfiguration.licenseFindingCurations.size -
                    licenseFindingCurations.size

            appendLine("Removed entries:")
            appendLine()
            appendLine("  path excludes             : $removedPathExcludes")
            appendLine("  license finding curations : $removedLicenseFindingCurations")
        }.let { println(it) }
    }
}

private fun ScanResult.getAllFiles(): List<String> =
    with(summary) {
        licenseFindings.map { it.location.path } + copyrightFindings.map { it.location.path }
    }.distinct()
