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
import org.ossreviewtoolkit.helper.utils.RepositoryLicenseFindingCurations
import org.ossreviewtoolkit.helper.utils.VcsUrlMapping
import org.ossreviewtoolkit.helper.utils.getLicenseFindingCurationsByRepository
import org.ossreviewtoolkit.helper.utils.mapLicenseFindingCurationsVcsUrls
import org.ossreviewtoolkit.helper.utils.mergeLicenseFindingCurations
import org.ossreviewtoolkit.helper.utils.orEmpty
import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.helper.utils.replaceConfig
import org.ossreviewtoolkit.helper.utils.write
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.expandTilde

internal class ExportLicenseFindingCurationsCommand : OrtHelperCommand(
    help = "Export the license finding curations to a file which maps repository URLs to the license finding " +
        "curations for the respective repository."
) {
    private val licenseFindingCurationsFile by option(
        "--license-finding-curations-file",
        help = "The output license finding curations file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val ortFile by option(
        "--ort-file", "-i",
        help = "The input ORT file from which the license finding curations are to be read."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .required()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "Override the repository configuration contained in the given input ORT file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val updateOnlyExisting by option(
        "--update-only-existing",
        help = "If enabled, only entries are imported for which an entry already exists which differs only in terms " +
            "of its concluded license, comment or reason."
    ).flag()

    private val vcsUrlMappingFile by option(
        "--vcs-url-mapping-file",
        help = "A YAML or JSON file containing a mapping of VCS URLs to other VCS URLs which will be replaced during " +
            "the export."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }

    override fun run() {
        val ortResult = readOrtResult(ortFile).replaceConfig(repositoryConfigurationFile)
        val vcsUrlMapping = vcsUrlMappingFile?.readValue<VcsUrlMapping>().orEmpty()

        val localLicenseFindingCurations = getLicenseFindingCurationsByRepository(
            curations = ortResult.repository.config.curations.licenseFindings,
            nestedRepositories = ortResult.repository.nestedRepositories
        ).mapLicenseFindingCurationsVcsUrls(vcsUrlMapping)

        val globalLicenseFindingCurations = if (licenseFindingCurationsFile.isFile) {
            licenseFindingCurationsFile.readValue<RepositoryLicenseFindingCurations>()
        } else {
            mapOf()
        }.mapLicenseFindingCurationsVcsUrls(vcsUrlMapping)

        globalLicenseFindingCurations
            .mergeLicenseFindingCurations(localLicenseFindingCurations, updateOnlyExisting = updateOnlyExisting)
            .write(licenseFindingCurationsFile)
    }
}
