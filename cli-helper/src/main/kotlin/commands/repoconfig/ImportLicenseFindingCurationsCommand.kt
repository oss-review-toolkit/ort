/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.commands.repoconfig

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.VcsUrlMapping
import org.ossreviewtoolkit.helper.utils.getRepositoryPaths
import org.ossreviewtoolkit.helper.utils.importLicenseFindingCurations
import org.ossreviewtoolkit.helper.utils.mergeLicenseFindingCurations
import org.ossreviewtoolkit.helper.utils.orEmpty
import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.helper.utils.replaceLicenseFindingCurations
import org.ossreviewtoolkit.helper.utils.sortLicenseFindingCurations
import org.ossreviewtoolkit.helper.utils.write
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.FindingCurationMatcher
import org.ossreviewtoolkit.utils.common.expandTilde

internal class ImportLicenseFindingCurationsCommand : OrtHelperCommand(
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

    private val ortFile by option(
        "--ort-file", "-i",
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

    private val vcsUrlMappingFile by option(
        "--vcs-url-mapping-file",
        help = "A YAML or JSON file containing a mapping of VCS URLs to other VCS URLs which will be replaced during " +
            "the import."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }

    private val findingCurationMatcher = FindingCurationMatcher()

    override fun run() {
        val ortResult = readOrtResult(ortFile)
        val repositoryConfiguration = if (repositoryConfigurationFile.isFile) {
            repositoryConfigurationFile.readValue()
        } else {
            RepositoryConfiguration()
        }

        val vcsUrlMapping = vcsUrlMappingFile?.readValue<VcsUrlMapping>().orEmpty()

        val allLicenseFindings = ortResult.getLicenseFindingsForAllProjects()
        val repositoryPaths = ortResult.getRepositoryPaths()
        val importedCurations = importLicenseFindingCurations(
            repositoryPaths,
            licenseFindingCurationsFile,
            vcsUrlMapping
        ).filter { curation ->
            allLicenseFindings.any { finding -> findingCurationMatcher.matches(finding, curation) }
        }

        val existingCurations = repositoryConfiguration.curations.licenseFindings
        val curations = existingCurations.mergeLicenseFindingCurations(importedCurations, updateOnlyExisting)

        repositoryConfiguration
            .replaceLicenseFindingCurations(curations)
            .sortLicenseFindingCurations()
            .write(repositoryConfigurationFile)
    }
}

private fun OrtResult.getLicenseFindingsForAllProjects(): Set<LicenseFinding> {
    val result = mutableSetOf<LicenseFinding>()

    val projectIds = getProjects().mapTo(mutableSetOf()) { it.id }
    getScanResults().forEach { (id, results) ->
        if (id in projectIds) {
            results.forEach { scanResult ->
                result += scanResult.summary.licenseFindings
            }
        }
    }

    return result
}
