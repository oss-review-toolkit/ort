/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.githubaction

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.div

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

private const val PROJECT_TYPE = "GitHubAction"
internal const val PACKAGE_TYPE = "GitHubAction"

internal object ABomCommand : CommandLineTool {
    override fun command(workingDir: File?) = "abom"

    override fun transformVersion(output: String) = output.removePrefix("abom version ")

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=0.2.0")
}

@OrtPlugin(
    displayName = "GitHub Action",
    summary = "Analyze dependencies of a GitHub Action.",
    factory = PackageManagerFactory::class
)
class GitHubAction(
    override val descriptor: PluginDescriptor = GitHubActionFactory.descriptor
) : PackageManager(PROJECT_TYPE) {
    override val globsForDefinitionFiles = listOf(".github/workflows/*.{yml,yaml}")

    private val aBomCache = mutableMapOf<File, ABom.Workflow>()
    private lateinit var projectVcs: VcsInfo
    private val graphBuilder by lazy { DependencyGraphBuilder(GitHubActionDependencyHandler(projectVcs)) }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        projectVcs = processProjectVcs(definitionFile.parentFile)

        val workflow = aBomCache.getOrElse(definitionFile) {
            val projectRoot = definitionFile.parentFile.parentFile.parentFile

            val scanProcess = ABomCommand.run(
                // The command must be run on the directory that contains the ".github/workflows" subdirectory.
                "scan", projectRoot.absolutePath,
                // The default depth of 10 is a bit low; use a higher value that is effectively unlimited.
                "--depth", "1000",
                "--output", "json"
            ).requireSuccess()

            val aBom = JSON.decodeFromString<ABom>(scanProcess.stdout)

            logger.info { aBom.abom }
            logger.info { aBom.summary }

            // The abom tool always scans all workflows in a directory, while ORT iterates over workflow / definition
            // files one by one.
            aBom.workflows.associateByTo(aBomCache) { workflow -> projectRoot / workflow.path }

            aBomCache[definitionFile] ?: return emptyList()
        }

        // Map workflows to projects.
        val projectName = workflow.name.ifEmpty { File(workflow.path).nameWithoutExtension }
        val projectId = Identifier(PROJECT_TYPE, "", projectName, "")

        // Map jobs to scopes.
        workflow.jobs.forEach { job ->
            // Map actions to dependencies.
            val dependencies = job.steps.mapNotNull { step -> step.action }
            graphBuilder.addDependencies(projectId, job.id, dependencies)
        }

        val project = Project(
            id = projectId,
            definitionFilePath = workflow.path,
            declaredLicenses = emptySet(),
            vcs = projectVcs,
            description = workflow.name,
            homepageUrl = vcsToHomepageUrl(projectVcs.url),
            scopeNames = graphBuilder.scopesFor(projectId)
        )

        return listOf(ProjectAnalyzerResult(project, emptySet()))
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
}
