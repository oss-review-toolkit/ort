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
import com.here.ort.helper.common.findFilesRecursive
import com.here.ort.helper.common.minimize
import com.here.ort.helper.common.replacePathExcludes
import com.here.ort.helper.common.replaceRuleViolationResolutions
import com.here.ort.helper.common.replaceScopeExcludes
import com.here.ort.helper.common.writeAsYaml
import com.here.ort.model.OrtResult
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY

import java.io.File

@Parameters(
    commandNames = ["remove-configuration-entries"],
    commandDescription = "Removes all non-matching path and scope excludes as well as rule violation resolutions." +
            "The output is written to the given repository configuration file."
)
internal class RemoveConfigurationEntriesCommand : CommandWithHelp() {
    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The ORT result file to read as input which should contain an evaluator result."
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--repository-configuration-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The repository configuration to remove all non-matching entries from. Its initial content " +
                "overrides the repository configuration contained in the given ORT result file."
    )
    private lateinit var repositoryConfigurationFile: File

    @Parameter(
        names = ["--source-code-dir"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "A directory containing the sources of the project(s) for which the configuration entries are " +
                "are to be removed. The provenance of these sources must match with the scan results contained in " +
                "the given ORT result file."
    )
    private lateinit var sourceCodeDir: File

    override fun runCommand(jc: JCommander): Int {
        var repositoryConfiguration = repositoryConfigurationFile.readValue<RepositoryConfiguration>()
        val ortResult = ortResultFile.readValue<OrtResult>().replaceConfig(repositoryConfiguration)

        val scopeExcludes = ortResult
            .getProjects()
            .flatMap { project -> project.scopes.map { it.name } }
            .let { projectScopes -> ortResult.getExcludes().scopes.minimize(projectScopes) }
        repositoryConfiguration = repositoryConfiguration.replaceScopeExcludes(scopeExcludes)

        val pathExcludes = findFilesRecursive(sourceCodeDir).let { allFiles ->
            ortResult.getExcludes().paths.filter { pathExclude ->
                allFiles.any { pathExclude.matches(it) }
            }
        }
        repositoryConfiguration = repositoryConfiguration.replacePathExcludes(pathExcludes)

        val ruleViolationResolutions = ortResult.getRuleViolations().let { ruleViolations ->
            ortResult.getResolutions().ruleViolations.filter { resolutions ->
                ruleViolations.any { resolutions.matches(it) }
            }
        }
        repositoryConfiguration = repositoryConfiguration.replaceRuleViolationResolutions(ruleViolationResolutions)

        // TODO: Implement the removal of not needed error resolutions.

        repositoryConfiguration.writeAsYaml(repositoryConfigurationFile)

        return 0
    }
}
