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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.findFilesRecursive
import org.ossreviewtoolkit.helper.common.minimize
import org.ossreviewtoolkit.helper.common.replaceIssueResolutions
import org.ossreviewtoolkit.helper.common.replacePathExcludes
import org.ossreviewtoolkit.helper.common.replaceRuleViolationResolutions
import org.ossreviewtoolkit.helper.common.replaceScopeExcludes
import org.ossreviewtoolkit.helper.common.writeAsYaml
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.utils.expandTilde

internal class RemoveConfigurationEntriesCommand : CliktCommand(
    help = "Removes all non-matching path and scope excludes as well as rule violation resolutions. The output is " +
            "written to the given repository configuration file."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input which should contain an evaluator result."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "The repository configuration to remove all non-matching entries from. Its initial content overrides " +
                "the repository configuration contained in the given ORT result file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = true, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val sourceCodeDir by option(
        "--source-code-dir",
        help = "A directory containing the sources of the project(s) for which the configuration entries are to be " +
                "removed. The provenance of these sources must match with the scan results contained in the given " +
                "ORT result file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val resolutionsFile by option(
        "--resolutions-file",
        help = "A file containing issue resolutions."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    override fun run() {
        val repositoryConfiguration = repositoryConfigurationFile.readValue<RepositoryConfiguration>()
        val ortResult = ortFile.readValue<OrtResult>().replaceConfig(repositoryConfiguration)

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
            resolutionsFile?.readValue<Resolutions>()?.let { add(it) }
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

            appendLine("Removed entries:")
            appendLine()
            appendLine("  path excludes             : $removedPathExcludes")
            appendLine("  scope excludes            : $removedScopeExcludes")
            appendLine("  issue resolutions         : $removedIssueResolutions")
            appendLine("  rule violation resolutions: $removedRuleViolationResolutions")
        }.let { println(it) }
    }
}
