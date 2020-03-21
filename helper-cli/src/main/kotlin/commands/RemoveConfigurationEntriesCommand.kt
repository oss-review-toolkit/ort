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

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import org.ossreviewtoolkit.helper.CommandWithHelp
import org.ossreviewtoolkit.helper.common.findFilesRecursive
import org.ossreviewtoolkit.helper.common.minimize
import org.ossreviewtoolkit.helper.common.replacePathExcludes
import org.ossreviewtoolkit.helper.common.replaceIssueResolutions
import org.ossreviewtoolkit.helper.common.replaceRuleViolationResolutions
import org.ossreviewtoolkit.helper.common.replaceScopeExcludes
import org.ossreviewtoolkit.helper.common.writeAsYaml
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.reporter.DefaultResolutionProvider
import org.ossreviewtoolkit.utils.PARAMETER_ORDER_MANDATORY
import org.ossreviewtoolkit.utils.PARAMETER_ORDER_OPTIONAL
import org.ossreviewtoolkit.utils.expandTilde

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

    @Parameter(
        description = "A file containing issue resolutions.",
        names = ["--resolutions-file"],
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var resolutionsFile: File? = null

    override fun runCommand(jc: JCommander): Int {
        val repositoryConfiguration = repositoryConfigurationFile.readValue<RepositoryConfiguration>()
        val ortResult = ortResultFile.readValue<OrtResult>().replaceConfig(repositoryConfiguration)

        val pathExcludes = findFilesRecursive(sourceCodeDir).let { allFiles ->
            ortResult.getExcludes().paths.filter { pathExclude ->
                allFiles.any { pathExclude.matches(it) }
            }
        }

        val scopeExcludes = ortResult
            .getProjects()
            .flatMap { project -> project.scopes.map { it.name } }
            .let { projectScopes -> ortResult.getExcludes().scopes.minimize(projectScopes) }

        val ruleViolationResolutions = ortResult.getRuleViolations().let { ruleViolations ->
            ortResult.getResolutions().ruleViolations.filter { resolution ->
                ruleViolations.any { resolution.matches(it) }
            }
        }

        val resolutionProvider = DefaultResolutionProvider().apply {
            resolutionsFile?.expandTilde()?.readValue<Resolutions>()?.let {
                add(it)
            }
        }
        val notGloballyResolvedIssues = ortResult.collectIssues().values.flatten().filter {
            resolutionProvider.getIssueResolutionsFor(it).isEmpty()
        }
        val issueResolutions = ortResult.getResolutions().issues.filter { resolution ->
            notGloballyResolvedIssues.any { resolution.matches(it) }
        }

        repositoryConfiguration
            .replacePathExcludes(pathExcludes)
            .replaceScopeExcludes(scopeExcludes)
            .replaceIssueResolutions(issueResolutions)
            .replaceRuleViolationResolutions(ruleViolationResolutions)
            .writeAsYaml(repositoryConfigurationFile)

        buildString {
            val removedPathExcludes = repositoryConfiguration.excludes.paths.size - pathExcludes.size
            val removedScopeExcludes = repositoryConfiguration.excludes.scopes.size - scopeExcludes.size
            val removedIssueResolutions = repositoryConfiguration.resolutions.issues.size - issueResolutions.size
            val removedRuleViolationResolutions = repositoryConfiguration.resolutions.ruleViolations.size -
                    ruleViolationResolutions.size

            appendln("Removed entries:")
            appendln()
            appendln("  path excludes             : $removedPathExcludes")
            appendln("  scope excludes            : $removedScopeExcludes")
            appendln("  issue resolutions         : $removedIssueResolutions")
            appendln("  rule violation resolutions: $removedRuleViolationResolutions")
        }.let { println(it) }

        return 0
    }
}
