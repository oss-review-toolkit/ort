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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.findFilesRecursive
import org.ossreviewtoolkit.helper.utils.minimize
import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.helper.utils.replaceIssueResolutions
import org.ossreviewtoolkit.helper.utils.replaceLicenseFindingCurations
import org.ossreviewtoolkit.helper.utils.replacePathExcludes
import org.ossreviewtoolkit.helper.utils.replaceRuleViolationResolutions
import org.ossreviewtoolkit.helper.utils.replaceScopeExcludes
import org.ossreviewtoolkit.helper.utils.write
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.FindingCurationMatcher
import org.ossreviewtoolkit.utils.common.expandTilde

internal class RemoveEntriesCommand : OrtHelperCommand(
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

    private val findingsMatcher = FindingCurationMatcher()

    override fun run() {
        val repositoryConfiguration = repositoryConfigurationFile.readValue<RepositoryConfiguration>()
        val ortResult = readOrtResult(ortFile).replaceConfig(repositoryConfiguration)

        val pathExcludes = findFilesRecursive(sourceCodeDir).let { allFiles ->
            ortResult.getExcludes().paths.filter { pathExclude ->
                allFiles.any { pathExclude.matches(it) }
            }
        }

        val scopeExcludes = ortResult
            .getProjects()
            .flatMapTo(mutableSetOf()) { project -> project.scopes.map { scope -> scope.name } }
            .let { projectScopes -> ortResult.getExcludes().scopes.minimize(projectScopes) }

        val licenseFindings = ortResult.getProjectLicenseFindings()
        val licenseFindingCurations = ortResult.repository.config.curations.licenseFindings.filter { curation ->
            licenseFindings.any { finding -> findingsMatcher.matches(finding, curation) }
        }

        val ruleViolationResolutions = ortResult.getRuleViolations().let { ruleViolations ->
            ortResult.getRepositoryConfigResolutions().ruleViolations.filter { resolution ->
                ruleViolations.any { resolution.matches(it) }
            }
        }

        val resolutionProvider = DefaultResolutionProvider.create(resolutionsFile = resolutionsFile)
        val notGloballyResolvedIssues = ortResult.getIssues().values.flatten().filterNot {
            resolutionProvider.isResolved(it)
        }

        val issueResolutions = ortResult.getRepositoryConfigResolutions().issues.filter { resolution ->
            notGloballyResolvedIssues.any { resolution.matches(it) }
        }

        repositoryConfiguration
            .replacePathExcludes(pathExcludes)
            .replaceScopeExcludes(scopeExcludes)
            .replaceLicenseFindingCurations(licenseFindingCurations)
            .replaceIssueResolutions(issueResolutions)
            .replaceRuleViolationResolutions(ruleViolationResolutions)
            .write(repositoryConfigurationFile)

        buildString {
            val removedPathExcludes = repositoryConfiguration.excludes.paths.size - pathExcludes.size
            val removedScopeExcludes = repositoryConfiguration.excludes.scopes.size - scopeExcludes.size
            val removedLicenseFindingCurations = repositoryConfiguration.curations.licenseFindings.size -
                licenseFindingCurations.size
            val removedIssueResolutions = repositoryConfiguration.resolutions.issues.size - issueResolutions.size
            val removedRuleViolationResolutions = repositoryConfiguration.resolutions.ruleViolations.size -
                ruleViolationResolutions.size

            appendLine("Removed entries:")
            appendLine()
            appendLine("  path excludes             : $removedPathExcludes")
            appendLine("  scope excludes            : $removedScopeExcludes")
            appendLine("  license finding curations : $removedLicenseFindingCurations")
            appendLine("  issue resolutions         : $removedIssueResolutions")
            appendLine("  rule violation resolutions: $removedRuleViolationResolutions")
        }.let { println(it) }
    }
}

private fun OrtResult.getProjectLicenseFindings(): List<LicenseFinding> =
    getProjects().flatMap { project ->
        val path = getDefinitionFilePathRelativeToAnalyzerRoot(project).substringBeforeLast("/")
        val scanResults = getScanResultsForId(project.id)

        scanResults.flatMap { scanResult ->
            scanResult.summary.licenseFindings.map { finding ->
                if (path.isBlank()) {
                    finding
                } else {
                    finding.copy(
                        location = finding.location.copy(
                            path = "$path/${finding.location.path}"
                        )
                    )
                }
            }
        }
    }.distinct()
