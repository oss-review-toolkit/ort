/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.cocoapods

import java.io.File

import kotlin.io.resolveSibling

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.stashDirectories

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

private const val LOCKFILE_FILENAME = "Podfile.lock"
private const val SCOPE_NAME = "dependencies"

internal object CocoaPodsCommand : CommandLineTool {
    override fun command(workingDir: File?) = if (Os.isWindows) "pod.bat" else "pod"

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=1.11.0")

    override fun getVersionArguments() = "--version --allow-root"
}

/**
 * The [CocoaPods](https://cocoapods.org/) package manager for Objective-C.
 *
 * As pre-condition for the analysis each respective definition file must have a sibling lockfile named 'Podfile.lock'.
 * The dependency tree is constructed solely based on parsing that lockfile. So, the dependency tree can be constructed
 * on any platform. Note that obtaining the dependency tree from the 'pod' command without a lockfile has Xcode
 * dependencies and is not supported by this class.
 *
 * The only interactions with the 'pod' command happen in order to obtain metadata for dependencies. Therefore,
 * 'pod spec which' gets executed, which works also under Linux.
 */
@OrtPlugin(
    displayName = "CocoaPods",
    description = "The CocoaPods package manager for Objective-C.",
    factory = PackageManagerFactory::class
)
class CocoaPods(override val descriptor: PluginDescriptor = CocoaPodsFactory.descriptor) : PackageManager("CocoaPods") {
    override val globsForDefinitionFiles = listOf("Podfile")

    private val dependencyHandler = PodDependencyHandler()
    private val graphBuilder = DependencyGraphBuilder(dependencyHandler)

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) = CocoaPodsCommand.checkVersion()

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> =
        stashDirectories(Os.userHomeDirectory / ".cocoapods" / "repos").use {
            // Ensure to use the CDN instead of the monolithic specs repo.
            CocoaPodsCommand.run("repo", "add-cdn", "trunk", "https://cdn.cocoapods.org", "--allow-root")
                .requireSuccess()

            try {
                resolveDependenciesInternal(analysisRoot, definitionFile)
            } finally {
                // The cache entries are not re-usable across definition files because the keys do not contain the
                // dependency version. If non-default Specs repositories were supported, then these would also need to
                // be part of the key. As that's more complicated and not giving much performance prefer the more memory
                // consumption friendly option of clearing the cache.
                dependencyHandler.clearPodspecCache()
            }
        }

    private fun resolveDependenciesInternal(analysisRoot: File, definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val lockfile = workingDir / LOCKFILE_FILENAME
        val issues = mutableListOf<Issue>()

        val projectId = Identifier(
            type = projectType,
            namespace = "",
            name = getFallbackProjectName(analysisRoot, definitionFile),
            version = ""
        )

        val project = Project(
            id = projectId,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = emptySet(),
            declaredLicenses = emptySet(),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = processProjectVcs(workingDir),
            homepageUrl = "",
            scopeNames = setOf(SCOPE_NAME)
        )

        if (lockfile.isFile) {
            val lockfileData = lockfile.readText().parseLockfile()

            // Resolve paths of external sources relative to the lockfile.
            val lockfileWithResolvedPaths = lockfileData.withResolvedPaths(lockfile)

            // Convert direct dependencies with version constraints to pods with resolved versions.
            val dependencies = lockfileWithResolvedPaths.dependencies.mapNotNull {
                it.resolvedPod?.run {
                    lockfileWithResolvedPaths.Pod(
                        name,
                        version,
                        dependencies
                    )
                }
            }

            graphBuilder.addDependencies(projectId, SCOPE_NAME, dependencies)
        } else {
            issues += createAndLogIssue(
                "Missing lockfile '${lockfile.relativeTo(analysisRoot).invariantSeparatorsPath}' for definition file " +
                    "'${definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath}'. The analysis of a Podfile " +
                    "without a lockfile is not supported."
            )
        }

        return listOf(ProjectAnalyzerResult(project, emptySet(), issues))
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
}

/**
 * Return a new [Lockfile] instance with all external source paths resolved relative to the given [lockfilePath].
 */
internal fun Lockfile.withResolvedPaths(lockfilePath: File): Lockfile {
    val resolvedExternalSources = externalSources.mapValues { entry ->
        Lockfile.ExternalSource(
            entry.value.path?.let { lockfilePath.resolveSibling(it).path },
            entry.value.podspec?.let { lockfilePath.resolveSibling(it).path }
        )
    }

    val pods = mutableListOf<Lockfile.Pod>()
    val dependencies = mutableListOf<Lockfile.Dependency>()

    val lockFile = Lockfile(pods, dependencies, resolvedExternalSources, checkoutOptions)

    this.pods.forEach { pod ->
        val resolvedPod = lockFile.Pod(
            pod.name,
            pod.version,
            pod.dependencies.map { dependency ->
                lockFile.Dependency(
                    dependency.name,
                    dependency.versionConstraint
                )
            }
        )

        pods += resolvedPod
    }

    this.dependencies.forEach { dependency ->
        val resolvedDependency = lockFile.Dependency(dependency.name, dependency.versionConstraint)

        dependencies += resolvedDependency
    }

    return lockFile
}
