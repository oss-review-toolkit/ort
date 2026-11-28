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

import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

private const val PROJECT_TYPE = "GitHubAction"
internal const val PACKAGE_TYPE = "GitHubAction"
internal const val DOCKER_PACKAGE_TYPE = "Docker"

private const val GITHUB_WORKFLOWS_DIR = ".github/workflows"
private val ACTION_FILE_NAMES = setOf("action.yml", "action.yaml")

/** The name of the scope that contains the dependencies of a composite action. */
private const val ACTION_SCOPE = "runs"

internal object ABomCommand : CommandLineTool {
    override fun command(workingDir: File?) = "abom"

    override fun transformVersion(output: String) = output.removePrefix("abom version ")

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=0.2.0")
}

@OrtPlugin(
    displayName = "GitHub Action",
    summary = "Analyze dependencies of GitHub Actions workflows and of GitHub Actions themselves.",
    factory = PackageManagerFactory::class
)
class GitHubAction(
    override val descriptor: PluginDescriptor = GitHubActionFactory.descriptor
) : PackageManager(PROJECT_TYPE) {
    override val globsForDefinitionFiles = listOf("$GITHUB_WORKFLOWS_DIR/*.{yml,yaml}", "action.{yml,yaml}")

    private val aBomCache = mutableMapOf<File, ABom.Workflow>()
    private val actionFiles = mutableListOf<File>()

    // Also cache a failure to resolve the dependencies of actions to not run abom again for each action.
    private val actionDependencies by lazy { runCatching { resolveActionDependencies(actionFiles) } }
    private lateinit var projectVcs: VcsInfo
    private val graphBuilderDelegate = lazy { DependencyGraphBuilder(GitHubActionDependencyHandler(projectVcs)) }
    private val graphBuilder by graphBuilderDelegate

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> {
        // Only keep workflows that are actually visible to GitHub.
        val (workflows, others) = definitionFiles.partition { isWorkflowFile(it) }
        val (visible, invisible) = workflows.partition {
            it.toRelativeString(analysisRoot).startsWith("$GITHUB_WORKFLOWS_DIR/")
        }

        if (invisible.isNotEmpty()) {
            logger.debug {
                "Ignoring the following workflows that are not in the root's '$GITHUB_WORKFLOWS_DIR' directory: " +
                    invisible.joinToString()
            }
        }

        // In contrast to workflows, actions can be located in any directory.
        val actions = others.filter { it.name in ACTION_FILE_NAMES }

        return visible + actions
    }

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) {
        actionFiles += definitionFiles.filterNot { isWorkflowFile(it) }
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        projectVcs = processProjectVcs(definitionFile.parentFile)

        return if (isWorkflowFile(definitionFile)) {
            resolveWorkflow(definitionFile)
        } else {
            resolveAction(analysisRoot, definitionFile)
        }
    }

    private fun resolveWorkflow(definitionFile: File): List<ProjectAnalyzerResult> {
        val workflow = aBomCache.getOrElse(definitionFile) {
            // The command must be run on the directory that contains the ".github/workflows" subdirectory.
            val projectRoot = definitionFile.parentFile.parentFile.parentFile
            val aBom = runABom(projectRoot)

            // The abom tool always scans all workflows in a directory, while ORT iterates over workflow / definition
            // files one by one.
            aBom.workflows.associateByTo(aBomCache) { workflow -> projectRoot / workflow.path }

            aBomCache[definitionFile] ?: return emptyList()
        }

        // Map workflows to projects.
        val workflowBaseName = File(workflow.path).nameWithoutExtension
        val projectName = workflow.name.ifEmpty { workflowBaseName }
        val projectId = Identifier(PROJECT_TYPE, workflowBaseName.encodeColons(), projectName.encodeColons(), "")

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

    private fun resolveAction(analysisRoot: File, definitionFile: File): List<ProjectAnalyzerResult> {
        val action = parseActionFile(definitionFile)

        if (action.runs == null) {
            logger.info { "Ignoring '$definitionFile' as it does not define how to run an action." }
            return emptyList()
        }

        // Use the path to the action file as the namespace, as there can be multiple actions with the same name. In
        // contrast to the base name of a workflow file, which is the namespace of workflows, it contains a slash or an
        // extension, so the identifiers of actions and workflows do not clash.
        val namespace = definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath
        val projectName = action.name.ifEmpty { definitionFile.parentFile.name }
        val projectId = Identifier(PROJECT_TYPE, namespace.encodeColons(), projectName.encodeColons(), "")
        val definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path

        // Only resolve the dependencies if needed, so that other actions can be analyzed even if resolution fails.
        val usesRemoteActions = action.usedActions.any { !isLocalActionReference(it) }
        val dependencies = if (usesRemoteActions) actionDependencies.getOrThrow()[definitionFile] else null

        val directLocalActions = action.usedActions.filter { isLocalActionReference(it) }.map { uses ->
            "The composite action '$definitionFilePath' uses the local action '$uses'."
        }

        val transitiveLocalActions = dependencies?.localActions.orEmpty().map { (user, uses) ->
            "The action '$user' used by the composite action '$definitionFilePath' uses the local action '$uses'."
        }

        val issues = (directLocalActions + transitiveLocalActions).map { message ->
            createAndLogIssue(
                message = "$message As this is resolved relative to the workspace of the calling workflow, it cannot " +
                    "be analyzed.",
                severity = Severity.WARNING
            )
        }

        if (action.isComposite) {
            graphBuilder.addDependencies(projectId, ACTION_SCOPE, dependencies?.actions.orEmpty())
        }

        val project = Project(
            id = projectId,
            definitionFilePath = definitionFilePath,
            authors = setOfNotNull(action.author.ifEmpty { null }),
            declaredLicenses = emptySet(),
            vcs = projectVcs,
            description = action.description,
            homepageUrl = vcsToHomepageUrl(projectVcs.url),
            scopeNames = graphBuilder.scopesFor(projectId)
        )

        return listOf(ProjectAnalyzerResult(project, emptySet(), issues))
    }

    /**
     * Resolve the dependencies of all composite actions in [files] with a single run of abom. As abom only supports
     * scanning workflows, create a synthetic workflow for each action that uses the same actions.
     */
    private fun resolveActionDependencies(files: List<File>): Map<File, ActionDependencies> {
        val usedActionsByFile = files.associateWith { file ->
            runCatching { parseActionFile(file).usedActions.filterNot { isLocalActionReference(it) } }
                .getOrDefault(emptyList())
        }.filterValues { it.isNotEmpty() }

        if (usedActionsByFile.isEmpty()) return emptyMap()

        val tempDir = createOrtTempDir()

        return try {
            val workflowsDir = tempDir / GITHUB_WORKFLOWS_DIR
            workflowsDir.mkdirs()

            val fileByWorkflowPath = usedActionsByFile.entries.withIndex().associate { (index, entry) ->
                val workflowName = "action-$index.yml"
                (workflowsDir / workflowName).writeText(createSyntheticWorkflow(entry.value))
                "$GITHUB_WORKFLOWS_DIR/$workflowName" to entry.key
            }

            runABom(tempDir).workflows.mapNotNull { workflow ->
                fileByWorkflowPath[workflow.path]?.let { file ->
                    val actions = workflow.jobs.flatMap { job -> job.steps.mapNotNull { step -> step.action } }
                    file to ActionDependencies.of(actions)
                }
            }.toMap()
        } finally {
            tempDir.safeDeleteRecursively()
        }
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        if (graphBuilderDelegate.isInitialized()) {
            PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
        } else {
            PackageManagerResult(projectResults)
        }
}

/** The dependencies of a composite action. */
internal data class ActionDependencies(
    /** The actions used by the composite action, including their transitive dependencies. */
    val actions: List<ABom.Action>,

    /**
     * The pairs of the reference to an action and the reference to a local action it uses, which were removed from the
     * transitive dependencies of [actions].
     */
    val localActions: List<Pair<String, String>>
) {
    companion object {
        /**
         * Create the dependencies of a composite action from the [actions] resolved by abom. Remove local actions from
         * their transitive dependencies, as these are resolved relative to the unknown workspace of the calling
         * workflow. Otherwise, they would be attributed to the analyzed repository.
         */
        fun of(actions: List<ABom.Action>): ActionDependencies {
            val localActions = mutableListOf<Pair<String, String>>()

            fun List<ABom.Action>.withoutLocalDependencies(): List<ABom.Action> =
                map { action ->
                    val (local, others) = action.dependencies.partition { it.type == ABom.ActionType.LOCAL }
                    local.mapTo(localActions) { action.uses to it.uses }
                    action.copy(dependencies = others.withoutLocalDependencies())
                }

            val actionsWithoutLocalDependencies = actions.withoutLocalDependencies()

            return ActionDependencies(actionsWithoutLocalDependencies, localActions.distinct())
        }
    }
}

/** Return whether [file] is located in a ".github/workflows" directory. */
private fun isWorkflowFile(file: File) = file.parentFile.invariantSeparatorsPath.endsWith("/$GITHUB_WORKFLOWS_DIR")

/** Run abom on the [projectRoot] directory that contains the ".github/workflows" subdirectory. */
private fun runABom(projectRoot: File): ABom {
    val scanProcess = ABomCommand.run(
        "scan", projectRoot.absolutePath,
        // The default depth of 10 is a bit low; use a higher value that is effectively unlimited.
        "--depth", "1000",
        "--output", "json"
    ).requireSuccess()

    val aBom = JSON.decodeFromString<ABom>(scanProcess.stdout)

    ABomCommand.logger.info { aBom.abom }
    ABomCommand.logger.info { aBom.summary }

    return aBom
}

/** Create a workflow with a single job whose steps use the given [actions]. JSON is used as it is valid YAML. */
private fun createSyntheticWorkflow(actions: List<String>): String =
    buildJsonObject {
        putJsonObject("jobs") {
            putJsonObject(ACTION_SCOPE) {
                putJsonArray("steps") {
                    actions.forEach { uses ->
                        addJsonObject { put("uses", uses) }
                    }
                }
            }
        }
    }.toString()
