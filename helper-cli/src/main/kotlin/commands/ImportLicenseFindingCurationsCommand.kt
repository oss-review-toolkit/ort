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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.RepositoryLicenseFindingCurations
import org.ossreviewtoolkit.helper.common.mergeLicenseFindingCurations
import org.ossreviewtoolkit.helper.common.replaceLicenseFindingCurations
import org.ossreviewtoolkit.helper.common.sortLicenseFindingCurations
import org.ossreviewtoolkit.helper.common.writeAsYaml
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.FindingCurationMatcher
import org.ossreviewtoolkit.utils.expandTilde

internal class ImportLicenseFindingCurationsCommand : CliktCommand(
    help = "Import license finding curations from a license finding curations file and merge them into the given "
            + "repository configuration."
) {
    private val licenseFindingCurationsFile by option(
        "--license-finding-curations-file",
        help = "The input license finding curations file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val ortResultFile by option(
        "--ort-result-file",
        help = "The ORT file containing the findings the imported curations need to match against."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "The repository configuration file where the imported curations are to be merged into."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val updateOnlyExisting by option(
        "--update-only-existing",
        help = "If enabled, only entries are imported for which an entry already exists which differs only in terms " +
                "of its concluded license, comment or reason."
    ).flag()

    private val findingCurationMatcher = FindingCurationMatcher()

    override fun run() {
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
        val existingCurations = repositoryConfiguration.curations.licenseFindings
        val curations = existingCurations.mergeLicenseFindingCurations(importedCurations, updateOnlyExisting)

        repositoryConfiguration
            .replaceLicenseFindingCurations(curations)
            .sortLicenseFindingCurations()
            .writeAsYaml(repositoryConfigurationFile)
    }

    private fun importLicenseFindingCurations(ortResult: OrtResult): Set<LicenseFindingCuration> {
        val repositoryPaths = ortResult.getRepositoryPaths()
        val licenseFindingCurations = licenseFindingCurationsFile.readValue<RepositoryLicenseFindingCurations>()

        val result = mutableSetOf<LicenseFindingCuration>()

        repositoryPaths.forEach { (vcsUrl, relativePaths) ->
            licenseFindingCurations[vcsUrl]?.let { curationsForRepository ->
                curationsForRepository.forEach { curation ->
                    relativePaths.forEach { path ->
                        result += curation.copy(path = path + '/' + curation.path)
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
        result.getOrPut(vcsInfo.url, { mutableSetOf() }) += path
    }

    return result
}

private fun OrtResult.getLicenseFindingsForAllProjects(): Set<LicenseFinding> {
    val result = mutableSetOf<LicenseFinding>()

    val projectIds = getProjects().mapTo(mutableSetOf()) { it.id }
    scanner?.results?.scanResults?.forEach { container ->
        if (container.id in projectIds) {
            container.results.forEach { scanResult ->
                result += scanResult.summary.licenseFindings
            }
        }
    }

    return result
}
