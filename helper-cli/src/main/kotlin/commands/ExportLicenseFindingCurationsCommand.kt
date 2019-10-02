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
import com.here.ort.helper.common.getRepositoryLicenseFindingCurations
import com.here.ort.helper.common.mergeLicenseFindingCurations
import com.here.ort.model.OrtResult
import com.here.ort.model.readValue
import com.here.ort.model.yamlMapper
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.safeMkdirs

import java.io.File

@Parameters(
    commandNames = ["export-license-finding-curations"],
    commandDescription = "Export the license finding curations to a file which maps repository URLs to the license " +
            "finding curations for the respective repository."
)
internal class ExportLicenseFindingCurationsCommand : CommandWithHelp() {
    @Parameter(
        names = ["--license-finding-curations-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The output license finding curations file."
    )
    private lateinit var licenseFindingCurationsFile: File

    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The input ORT file from which the license finding curations are to be read."
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--repository-configuration-file"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL,
        description = "Override the repository configuration contained in the given input ORT file."
    )
    private lateinit var repositoryConfigurationFile: File

    @Parameter(
        names = ["--update-only-existing"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL,
        description = "If enabled, only entries are imported for which an entry already exists which differs " +
            "only in terms of its concluded license, comment or reason."
    )
    private var updateOnlyExisting = false

    override fun runCommand(jc: JCommander): Int {
        val localLicenseFindingCurations = ortResultFile
            .readValue<OrtResult>()
            .replaceConfig(repositoryConfigurationFile.readValue())
            .getRepositoryLicenseFindingCurations()

        val globalLicenseFindingCurations = if (licenseFindingCurationsFile.isFile) {
            licenseFindingCurationsFile.readValue<RepositoryLicenseFindingCurations>()
        } else {
            mapOf()
        }

        globalLicenseFindingCurations
            .mergeLicenseFindingCurations(localLicenseFindingCurations, updateOnlyExisting = updateOnlyExisting)
            .writeAsYaml(licenseFindingCurationsFile)

        return 0
    }
}

/**
 * Serialize this [RepositoryLicenseFindingCurations] to the given [targetFile] as YAML.
 */
private fun RepositoryLicenseFindingCurations.writeAsYaml(targetFile: File) {
    targetFile.parentFile.apply { safeMkdirs() }

    yamlMapper.writeValue(
        targetFile,
        mapValues { (_, curations) ->
            curations.sortedBy { it.path.removePrefix("*").removePrefix("*") }
        }.toSortedMap()
    )
}
