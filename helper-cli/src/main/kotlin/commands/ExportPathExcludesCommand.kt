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
import com.here.ort.helper.common.RepositoryPathExcludes
import com.here.ort.helper.common.getRepositoryPathExcludes
import com.here.ort.helper.common.mergePathExcludes
import com.here.ort.model.OrtResult
import com.here.ort.model.readValue
import com.here.ort.model.yamlMapper
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.safeMkdirs

import java.io.File

@Parameters(
    commandNames = ["export-path-excludes"],
    commandDescription = "Export the path excludes to a path excludes file which maps repository URLs to the path " +
        "excludes for the respective repository."
)
internal class ExportPathExcludesCommand : CommandWithHelp() {
    @Parameter(
        names = ["--path-excludes-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var pathExcludesFile: File

    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--repository-configuration-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var repositoryConfigurationFile: File

    @Parameter(
        names = ["--update-only-existing"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var updateOnlyExisting = false

    override fun runCommand(jc: JCommander): Int {
        val localPathExcludes = ortResultFile
            .readValue<OrtResult>()
            .replaceConfig(repositoryConfigurationFile.readValue())
            .getRepositoryPathExcludes()

        val globalPathExcludes = if (pathExcludesFile.isFile) {
            pathExcludesFile.readValue<RepositoryPathExcludes>()
        } else {
            mapOf()
        }

        globalPathExcludes
            .mergePathExcludes(localPathExcludes, updateOnlyExisting = updateOnlyExisting)
            .writeAsYaml(pathExcludesFile)

        return 0
    }
}

/**
 * Serialize this [RepositoryPathExcludes] to the given [targetFile] as YAML.
 */
internal fun RepositoryPathExcludes.writeAsYaml(targetFile: File) {
    targetFile.parentFile.apply { safeMkdirs() }

    yamlMapper.writeValue(
        targetFile,
        mapValues { (_, pathExcludes) ->
            pathExcludes.sortedBy { it.pattern }
        }.toSortedMap()
    )
}
