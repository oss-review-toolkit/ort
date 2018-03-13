/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.analyzer.managers

import ch.frankel.slf4k.*

import com.here.ort.analyzer.MavenSupport
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.analyzer.identifier
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.Scope
import com.here.ort.utils.collectMessages
import com.here.ort.utils.log
import com.here.ort.utils.searchUpwardsForSubdirectory

import java.io.File

import org.apache.maven.project.ProjectBuilder
import org.apache.maven.project.ProjectBuildingException
import org.apache.maven.project.ProjectBuildingResult

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.metadata.Metadata
import org.eclipse.aether.repository.LocalArtifactRegistration
import org.eclipse.aether.repository.LocalArtifactRequest
import org.eclipse.aether.repository.LocalArtifactResult
import org.eclipse.aether.repository.LocalMetadataRegistration
import org.eclipse.aether.repository.LocalMetadataRequest
import org.eclipse.aether.repository.LocalMetadataResult
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.LocalRepositoryManager
import org.eclipse.aether.repository.RemoteRepository

class Maven : PackageManager() {
    companion object : PackageManagerFactory<Maven>(
            "https://maven.apache.org/",
            "Java",
            listOf("pom.xml")
    ) {
        override fun create() = Maven()
    }

    /**
     * Set of scope names for which [Scope.delivered] will be set to false by default. This can be changed later by
     * manually editing the output file.
     */
    private val assumedNonDeliveredScopes = setOf("test")

    private val maven = MavenSupport { localRepositoryManager ->
        LocalRepositoryManagerWrapper(localRepositoryManager)
    }

    private val projectsByIdentifier = mutableMapOf<String, ProjectBuildingResult>()

    private var sbtMode = false

    /**
     * Enable compatibility mode with POM files generated from SBT using "sbt makePom".
     */
    fun enableSbtMode() = this.apply { sbtMode = true }

    override fun command(workingDir: File) = "mvn"

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        val projectBuilder = maven.container.lookup(ProjectBuilder::class.java, "default")
        val projectBuildingRequest = maven.createProjectBuildingRequest(false)
        val projectBuildingResults = try {
            projectBuilder.build(definitionFiles, false, projectBuildingRequest)
        } catch (e: ProjectBuildingException) {
            if (com.here.ort.utils.printStackTrace) {
                e.printStackTrace()
            }

            log.warn {
                "There have been issues building the Maven project models, this could lead to errors during " +
                        "dependency analysis: ${e.message}"
            }
            e.results
        }

        projectBuildingResults.forEach { projectBuildingResult ->
            if (projectBuildingResult.project == null) {
                log.warn {
                    "Project for POM file '${projectBuildingResult.pomFile.absolutePath}' could not be built:\n" +
                            projectBuildingResult.problems.joinToString(separator = "\n")
                }
            } else {
                val project = projectBuildingResult.project
                val identifier = "${project.groupId}:${project.artifactId}:${project.version}"

                projectsByIdentifier[identifier] = projectBuildingResult
            }
        }

        return definitionFiles
    }

    override fun resolveDependencies(definitionFile: File): AnalyzerResult? {
        val workingDir = definitionFile.parentFile
        val projectBuildingResult = maven.buildMavenProject(definitionFile)
        val mavenProject = projectBuildingResult.project
        val packages = mutableMapOf<String, Package>()
        val scopes = mutableMapOf<String, Scope>()

        projectBuildingResult.dependencyResolutionResult.dependencyGraph.children.forEach { node ->
            val scopeName = node.dependency.scope
            val scope = scopes.getOrPut(scopeName) {
                Scope(scopeName, scopeName !in assumedNonDeliveredScopes, sortedSetOf())
            }

            scope.dependencies.add(parseDependency(node, packages))
        }

        val vcsFromPackage = maven.parseVcsInfo(mavenProject)

        // If running in SBT mode expect that POM files were generated in a "target" subdirectory and that the correct
        // project directory is the parent directory of this.
        val projectDir = if (sbtMode) {
            workingDir.searchUpwardsForSubdirectory("target") ?: workingDir
        } else {
            workingDir
        }

        val project = Project(
                id = Identifier(
                        provider = if (sbtMode) SBT.toString() else Maven.toString(),
                        namespace = mavenProject.groupId,
                        name = mavenProject.artifactId,
                        version = mavenProject.version
                ),
                declaredLicenses = maven.parseLicenses(mavenProject),
                aliases = emptyList(),
                vcs = vcsFromPackage,
                vcsProcessed = processProjectVcs(projectDir, vcsFromPackage),
                homepageUrl = mavenProject.url ?: "",
                scopes = scopes.values.toSortedSet()
        )

        return AnalyzerResult(true, project, packages.values.toSortedSet())
    }

    private fun parseDependency(node: DependencyNode, packages: MutableMap<String, Package>): PackageReference {
        try {
            val pkg = packages.getOrPut(node.artifact.identifier()) {
                maven.parsePackage(node.artifact, node.repositories,
                        projectsByIdentifier.mapValues { it.value.project }, sbtMode)
            }

            val dependencies = node.children.map { parseDependency(it, packages) }.toSortedSet()

            return pkg.toReference(dependencies)
        } catch (e: ProjectBuildingException) {
            if (com.here.ort.utils.printStackTrace) {
                e.printStackTrace()
            }

            log.error {
                "Could not get package information for dependency '${node.artifact.identifier()}': ${e.message}"
            }

            return PackageReference(
                    namespace = node.artifact.groupId,
                    name = node.artifact.artifactId,
                    version = node.artifact.version,
                    dependencies = sortedSetOf(),
                    errors = e.collectMessages()
            )
        }
    }

    /**
     * A wrapper for the [LocalRepositoryManager] used in [MavenSupport.repositorySystemSession] that pretends that the
     * POM files of the currently analyzed project are available in the local repository. Without it the resolution of
     * transitive dependencies of project dependencies would not work without installing the project in the local
     * repository first.
     */
    private inner class LocalRepositoryManagerWrapper(private val localRepositoryManager: LocalRepositoryManager)
        : LocalRepositoryManager {

        override fun add(session: RepositorySystemSession, request: LocalArtifactRegistration) =
                localRepositoryManager.add(session, request)

        override fun add(session: RepositorySystemSession, request: LocalMetadataRegistration) =
                localRepositoryManager.add(session, request)

        override fun find(session: RepositorySystemSession, request: LocalArtifactRequest): LocalArtifactResult {
            log.debug { "Request to find local artifact: $request" }

            projectsByIdentifier[request.artifact.identifier()]?.let {
                log.debug { "Found cached artifact: ${it.pomFile}" }
                val pomFile = File(it.pomFile.absolutePath)

                return LocalArtifactResult(request).apply {
                    file = pomFile
                    isAvailable = true
                }
            }

            return localRepositoryManager.find(session, request)
        }

        override fun find(session: RepositorySystemSession, request: LocalMetadataRequest): LocalMetadataResult =
                localRepositoryManager.find(session, request)

        override fun getPathForLocalArtifact(artifact: Artifact): String =
                localRepositoryManager.getPathForLocalArtifact(artifact)

        override fun getPathForLocalMetadata(metadata: Metadata)
                : String = localRepositoryManager.getPathForLocalMetadata(metadata)

        override fun getPathForRemoteArtifact(artifact: Artifact, repository: RemoteRepository, context: String)
                : String = localRepositoryManager.getPathForRemoteArtifact(artifact, repository, context)

        override fun getPathForRemoteMetadata(metadata: Metadata, repository: RemoteRepository, context: String)
                : String = localRepositoryManager.getPathForRemoteMetadata(metadata, repository, context)

        override fun getRepository(): LocalRepository = localRepositoryManager.repository
    }
}
