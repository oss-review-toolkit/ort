/*
 * Copyright (C) 2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.helper.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.helper.CommandWithHelp
import com.here.ort.helper.common.RepositoryLicenseFindingCurations
import com.here.ort.helper.common.mergeLicenseFindingCurations
import com.here.ort.helper.common.replaceLicenseFindingCurations
import com.here.ort.helper.common.sortLicenseFindingCurations
import com.here.ort.helper.common.writeAsYaml
import com.here.ort.model.LicenseFinding
import com.here.ort.model.OrtResult
import com.here.ort.model.config.LicenseFindingCuration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.readValue
import com.here.ort.model.util.FindingCurationMatcher
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL

import java.io.File

@Parameters(
    commandNames = ["import-license-finding-curations"],
    commandDescription = "Import license finding curations from a license finding curations file and merge them into " +
            "the given repository configuration."
)
internal class ImportLicenseFindingCurationsCommand : CommandWithHelp() {
    @Parameter(
        names = ["--license-finding-curations-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The input license finding curations file."
    )
    private lateinit var licenseFindingCurationsFile: File

    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The ORT file containing the findings the imported curations need to match against."
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--repository-configuration-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The repository configuration file where the imported curations are to be merged into."
    )
    private lateinit var repositoryConfigurationFile: File

    @Parameter(
        names = ["--update-only-existing"],
        order = PARAMETER_ORDER_OPTIONAL,
        description = "If enabled, only entries are imported for which an entry already exists which differs " +
                "only in terms of its concluded license, comment or reason."
    )
    private var updateOnlyExisting = false

    private val findingCurationMatcher = FindingCurationMatcher()

    override fun runCommand(jc: JCommander): Int {
        val ortResult = ortResultFile.readValue<OrtResult>()
        val repositoryConfiguration = if (repositoryConfigurationFile.isFile) {
            repositoryConfigurationFile.readValue()
        } else {
            RepositoryConfiguration()
        }

        val allLicenseFindings = ortResult.getLicenseFindingsForAllProjects()

        val importedCurations = importLicenseFindingCurations(ortResult)
            .filter { curation ->
                allLicenseFindings.any { finding ->
                    findingCurationMatcher.matches(finding, curation)
                }
            }
        val existingCurations = repositoryConfiguration.curations?.licenseFindings.orEmpty()
        val curations = existingCurations.mergeLicenseFindingCurations(importedCurations, updateOnlyExisting)

        repositoryConfiguration
            .replaceLicenseFindingCurations(curations)
            .sortLicenseFindingCurations()
            .writeAsYaml(repositoryConfigurationFile)

        return 0
    }

    private fun importLicenseFindingCurations(ortResult: OrtResult): Set<LicenseFindingCuration> {
        val repositoryPaths = ortResult.getRepositoryPaths()
        val licenseFindingCurations = licenseFindingCurationsFile.readValue<RepositoryLicenseFindingCurations>()

        val result = mutableSetOf<LicenseFindingCuration>()

        repositoryPaths.forEach { (vcsUrl, relativePaths) ->
            licenseFindingCurations[vcsUrl]?.let { curationsForRepository ->
                curationsForRepository.forEach { curation ->
                    relativePaths.forEach { path ->
                        result.add(curation.copy(path = path + '/' + curation.path))
                    }
                }
            }
        }

        return result
    }
}

private fun OrtResult.getRepositoryPaths(): Map<String, Set<String>> {
    val result = mutableMapOf<String, MutableSet<String>>()

    repository.nestedRepositories.mapValues { (path, vcsInfo) ->
        result.getOrPut(vcsInfo.url, { mutableSetOf() }).add(path)
    }

    return result
}

private fun OrtResult.getLicenseFindingsForAllProjects(): Set<LicenseFinding> {
    val result = mutableSetOf<LicenseFinding>()

    val projectIds = getProjects().mapTo(mutableSetOf()) { it.id }
    scanner?.results?.scanResults?.forEach { container ->
        if (container.id in projectIds) {
            container.results.forEach { scanResult ->
                result.addAll(scanResult.summary.licenseFindings)
            }
        }
    }

    return result
}
