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

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.stashDirectories

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

private const val LOCKFILE_FILENAME = "Podfile.lock"
private const val SCOPE_NAME = "dependencies"

internal object CocoaPodsCommand : CommandLineTool {
    override fun command(workingDir: File?) = if (Os.isWindows) "pod.bat" else "pod"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=1.11.0")

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
class CocoaPods(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "CocoaPodsProject", analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<CocoaPods>("CocoaPods") {
        override val globsForDefinitionFiles = listOf("Podfile")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = CocoaPods(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val dependencyHandler = PodDependencyHandler()
    private val graphBuilder = DependencyGraphBuilder(dependencyHandler)

    override fun beforeResolution(definitionFiles: List<File>) = CocoaPodsCommand.checkVersion()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> =
        stashDirectories(Os.userHomeDirectory.resolve(".cocoapods/repos")).use {
            // Ensure to use the CDN instead of the monolithic specs repo.
            CocoaPodsCommand.run("repo", "add-cdn", "trunk", "https://cdn.cocoapods.org", "--allow-root")
                .requireSuccess()

            try {
                resolveDependenciesInternal(definitionFile)
            } finally {
                // The cache entries are not re-usable across definition files because the keys do not contain the
                // dependency version. If non-default Specs repositories were supported, then these would also need to
                // be part of the key. As that's more complicated and not giving much performance prefer the more memory
                // consumption friendly option of clearing the cache.
                dependencyHandler.clearPodspecCache()
            }
        }

    private fun resolveDependenciesInternal(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val lockfile = workingDir.resolve(LOCKFILE_FILENAME)
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

            // Convert direct dependencies with version constraints to pods with resolved versions.
            val dependencies = lockfileData.dependencies.mapNotNull { it.resolvedPod }

            graphBuilder.addDependencies(projectId, SCOPE_NAME, dependencies)
        } else {
            issues += createAndLogIssue(
                source = managerName,
                message = "Missing lockfile '${lockfile.relativeTo(analysisRoot).invariantSeparatorsPath}' for " +
                    "definition file '${definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath}'. The " +
                    "analysis of a Podfile without a lockfile is not supported."
            )
        }

        return listOf(ProjectAnalyzerResult(project, emptySet(), issues))
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
}
