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
import com.here.ort.helper.common.replacePathExcludes
import com.here.ort.helper.common.sortPathExcludes
import com.here.ort.helper.common.writeAsYaml
import com.here.ort.model.OrtResult
import com.here.ort.model.config.PathExclude
import com.here.ort.model.config.PathExcludeReason
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY

import java.io.File

@Parameters(
    commandNames = ["generate-project-excludes"],
    commandDescription = "Generates path excludes for all definition files which are not yet excluded." +
        "The output is written to the given repository configuration file."
)
internal class GenerateProjectExcludesCommand : CommandWithHelp() {
    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The input ORT file from which the projects and repository configuration are read."
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--repository-configuration-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The repository configuration file to write the result to. If the file does already exist it " +
                "overrides the repository configuration contained in the given input ORT file."
    )
    private lateinit var repositoryConfigurationFile: File

    override fun runCommand(jc: JCommander): Int {
        val repositoryConfiguration = if (repositoryConfigurationFile.isFile) {
            repositoryConfigurationFile.readValue()
        } else {
            RepositoryConfiguration()
        }

        val ortResult = ortResultFile.readValue<OrtResult>()
            .replaceConfig(repositoryConfiguration)

        val generatedPathExcludes = ortResult
            .getProjects()
            .filterNot { ortResult.isExcluded(it.id) }
            .map { project ->
                PathExclude(
                    pattern = ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project),
                    reason = PathExcludeReason.OPTIONAL_COMPONENT_OF,
                    comment = "TODO"
                )
            }
        val existingPathExcludes = repositoryConfiguration.excludes?.paths ?: emptyList()
        val pathExcludes = (existingPathExcludes + generatedPathExcludes).distinctBy { it.pattern }

        repositoryConfiguration
            .replacePathExcludes(pathExcludes)
            .sortPathExcludes()
            .writeAsYaml(repositoryConfigurationFile)

        return 0
    }
}
