/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer.managers

import java.io.File

import org.apache.maven.project.ProjectBuildingResult

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.repository.WorkspaceRepository

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.managers.utils.MavenDependencyHandler
import org.ossreviewtoolkit.analyzer.managers.utils.MavenSupport
import org.ossreviewtoolkit.analyzer.managers.utils.identifier
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.utils.common.searchUpwardsForSubdirectory

/**
 * The [Maven](https://maven.apache.org/) package manager for Java.
 */
class Maven(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Maven>("Maven") {
        override val globsForDefinitionFiles = listOf("pom.xml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Maven(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    private inner class LocalProjectWorkspaceReader : WorkspaceReader {
        private val workspaceRepository = WorkspaceRepository()

        override fun findArtifact(artifact: Artifact) =
            artifact.takeIf { it.extension == "pom" }?.let {
                localProjectBuildingResults[it.identifier()]?.pomFile?.absoluteFile
            }

        override fun findVersions(artifact: Artifact) =
            // Avoid resolution of (SNAPSHOT) versions for local projects.
            localProjectBuildingResults[artifact.identifier()]?.let { listOf(artifact.version) }.orEmpty()

        override fun getRepository() = workspaceRepository
    }

    private val mvn = MavenSupport(LocalProjectWorkspaceReader())

    private val localProjectBuildingResults = mutableMapOf<String, ProjectBuildingResult>()

    /** The builder for the shared dependency graph. */
    private lateinit var graphBuilder: DependencyGraphBuilder<DependencyNode>

    private var sbtMode = false

    /**
     * Enable compatibility mode with POM files generated from SBT using "sbt makePom".
     */
    fun enableSbtMode() = also { sbtMode = true }

    override fun beforeResolution(definitionFiles: List<File>) {
        localProjectBuildingResults += mvn.prepareMavenProjects(definitionFiles)

        val localProjects = localProjectBuildingResults.mapValues { it.value.project }
        val dependencyHandler = MavenDependencyHandler(managerName, mvn, localProjects, sbtMode)
        graphBuilder = DependencyGraphBuilder(dependencyHandler)
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val projectBuildingResult = mvn.buildMavenProject(definitionFile)
        val mavenProject = projectBuildingResult.project
        val projectId = Identifier(
            type = managerName,
            namespace = mavenProject.groupId,
            name = mavenProject.artifactId,
            version = mavenProject.version
        )

        projectBuildingResult.dependencies.forEach { node ->
            graphBuilder.addDependency(DependencyGraph.qualifyScope(projectId, node.dependency.scope), node)
        }

        val declaredLicenses = MavenSupport.parseLicenses(mavenProject)
        val declaredLicensesProcessed = MavenSupport.processDeclaredLicenses(declaredLicenses)

        val vcsFromPackage = MavenSupport.parseVcsInfo(mavenProject)

        // If running in SBT mode expect that POM files were generated in a "target" subdirectory and that the correct
        // project directory is the parent directory of this.
        val projectDir = if (sbtMode) {
            workingDir.searchUpwardsForSubdirectory("target") ?: workingDir
        } else {
            workingDir
        }

        val browsableScmUrl = MavenSupport.getOriginalScm(mavenProject)?.url
        val homepageUrl = mavenProject.url
        val vcsFallbackUrls = listOfNotNull(browsableScmUrl, homepageUrl).toTypedArray()

        val project = Project(
            id = projectId,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = MavenSupport.parseAuthors(mavenProject),
            declaredLicenses = declaredLicenses,
            declaredLicensesProcessed = declaredLicensesProcessed,
            vcs = vcsFromPackage,
            vcsProcessed = processProjectVcs(projectDir, vcsFromPackage, *vcsFallbackUrls),
            homepageUrl = homepageUrl.orEmpty(),
            scopeNames = graphBuilder.scopesFor(projectId)
        )

        val packages = graphBuilder.packages().toSortedSet()
        val issues = packages.mapNotNull { pkg ->
            if (pkg.description == "POM was created by Sonatype Nexus") {
                createAndLogIssue(
                    managerName,
                    "Package '${pkg.id.toCoordinates()}' seems to use an auto-generated POM which might lack metadata.",
                    Severity.HINT
                )
            } else {
                null
            }
        }

        return listOf(ProjectAnalyzerResult(project, sortedSetOf(), issues))
    }
}

/**
 * Convenience extension property to obtain all the [DependencyNode]s from this [ProjectBuildingResult].
 */
private val ProjectBuildingResult.dependencies: List<DependencyNode>
    get() = dependencyResolutionResult.dependencyGraph.children
