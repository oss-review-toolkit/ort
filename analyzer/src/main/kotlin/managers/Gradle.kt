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

import Dependency
import DependencyTreeModel

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
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.utils.OS
import com.here.ort.utils.collectMessages
import com.here.ort.utils.log

import org.apache.maven.project.ProjectBuildingException

import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
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

import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnector

import java.io.File

class Gradle : PackageManager() {
    companion object : PackageManagerFactory<Gradle>(
            "https://gradle.org/",
            "Java",
            listOf("build.gradle", "settings.gradle")
    ) {
        override fun create() = Gradle()

        val gradle = if (OS.isWindows) "gradle.bat" else "gradle"
        val wrapper = if (OS.isWindows) "gradlew.bat" else "gradlew"
    }

    val maven = MavenSupport { localRepositoryManager ->
        GradleLocalRepositoryManager(localRepositoryManager)
    }

    override fun command(workingDir: File) = if (File(workingDir, wrapper).isFile) wrapper else gradle

    override fun resolveDependencies(definitionFile: File): AnalyzerResult? {
        val connection = GradleConnector
                .newConnector()
                .forProjectDirectory(definitionFile.parentFile)
                .connect()

        try {
            val initScriptFile = File.createTempFile("init", "gradle")
            initScriptFile.writeBytes(javaClass.classLoader.getResource("init.gradle").readBytes())

            val dependencyTreeModel = connection
                    .model(DependencyTreeModel::class.java)
                    .withArguments("--init-script", initScriptFile.absolutePath)
                    .get()

            if (!initScriptFile.delete()) {
                log.warn { "Init script file '${initScriptFile.absolutePath}' could not be deleted." }
            }

            val repositories = dependencyTreeModel.repositories.map {
                // TODO: Also handle authentication and snapshot policy.
                RemoteRepository.Builder(it, "default", it).build()
            }

            log.debug {
                "The Gradle project '${dependencyTreeModel.name}' uses the following Maven repositories: $repositories"
            }

            val packages = mutableMapOf<String, Package>()
            val scopes = dependencyTreeModel.configurations.map { configuration ->
                val dependencies = configuration.dependencies.map { dependency ->
                    parseDependency(dependency, packages, repositories)
                }

                Scope(configuration.name, true, dependencies.toSortedSet())
            }

            val project = Project(
                    id = Identifier(
                            provider = Gradle.toString(),
                            namespace = "",
                            name = dependencyTreeModel.name,
                            version = ""
                    ),
                    declaredLicenses = sortedSetOf(),
                    aliases = emptyList(),
                    vcs = VcsInfo.EMPTY,
                    vcsProcessed = processProjectVcs(definitionFile.parentFile),
                    homepageUrl = "",
                    scopes = scopes.toSortedSet()
            )

            return AnalyzerResult(true, project, packages.values.toSortedSet(),
                    dependencyTreeModel.errors)
        } catch (e: BuildException) {
            if (com.here.ort.utils.printStackTrace) {
                e.printStackTrace()
            }

            log.error { "Could not analyze '${definitionFile.absolutePath}': ${e.message}" }
            return null
        } finally {
            connection.close()
        }
    }

    private fun parseDependency(dependency: Dependency, packages: MutableMap<String, Package>,
                                repositories: List<RemoteRepository>): PackageReference {
        val errors = dependency.error?.let { mutableListOf(it) } ?: mutableListOf()

        // Only look for a package when there was no error resolving the dependency.
        if (dependency.error == null) {
            val identifier = "${dependency.groupId}:${dependency.artifactId}:${dependency.version}"

            val rawPackage by lazy {
                Package(
                        id = Identifier(
                                provider = "Maven",
                                namespace = dependency.groupId,
                                name = dependency.artifactId,
                                version = dependency.version
                        ),
                        declaredLicenses = sortedSetOf(),
                        description = "",
                        homepageUrl = "",
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VcsInfo.EMPTY
                )
            }

            packages.getOrPut(identifier) {
                val pkg = if (dependency.pomFile.isNotBlank()) {
                    val artifact = DefaultArtifact(dependency.groupId, dependency.artifactId, dependency.classifier,
                            dependency.extension, dependency.version)
                    try {
                        maven.parsePackage(artifact, repositories)
                    } catch (e: ProjectBuildingException) {
                        if (com.here.ort.utils.printStackTrace) {
                            e.printStackTrace()
                        }

                        log.error {
                            "Could not get package information for dependency '$identifier': ${e.message}"
                        }

                        errors.addAll(e.collectMessages())

                        rawPackage
                    }
                } else {
                    rawPackage
                }

                pkg.copy(vcsProcessed = processPackageVcs(pkg.vcs))
            }
        }

        val transitiveDependencies = dependency.dependencies.map { parseDependency(it, packages, repositories) }
        return PackageReference(dependency.groupId, dependency.artifactId, dependency.version,
                transitiveDependencies.toSortedSet(), errors)
    }

    /**
     * An implementation of [LocalRepositoryManager] that provides artifacts from the Gradle cache. This is required
     * to parse POM files from the Gradle cache, because Maven needs to be able to resolve parent POMs. Once the POM
     * has been parsed correctly all dependencies required to build the project model can be resolved by Maven using
     * the local repository.
     */
    private class GradleLocalRepositoryManager(private val localRepositoryManager: LocalRepositoryManager)
        : LocalRepositoryManager {

        private val gradleCacheRoot = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")

        override fun add(session: RepositorySystemSession, request: LocalArtifactRegistration) =
                localRepositoryManager.add(session, request)

        override fun add(session: RepositorySystemSession, request: LocalMetadataRegistration) =
                localRepositoryManager.add(session, request)

        override fun find(session: RepositorySystemSession, request: LocalArtifactRequest): LocalArtifactResult {
            log.debug { "Request to find local artifact: $request" }

            val file = findArtifactInGradleCache(request.artifact)

            if (file != null && file.isFile) {
                return LocalArtifactResult(request).apply {
                    this.file = file
                    isAvailable = true
                }
            }

            return localRepositoryManager.find(session, request)
        }

        override fun find(session: RepositorySystemSession, request: LocalMetadataRequest): LocalMetadataResult =
                localRepositoryManager.find(session, request)

        override fun getPathForLocalArtifact(artifact: Artifact): String =
                localRepositoryManager.getPathForLocalArtifact(artifact)

        override fun getPathForLocalMetadata(metadata: Metadata): String =
                localRepositoryManager.getPathForLocalMetadata(metadata)

        override fun getPathForRemoteArtifact(artifact: Artifact, repository: RemoteRepository, context: String)
                : String = localRepositoryManager.getPathForRemoteArtifact(artifact, repository, context)

        override fun getPathForRemoteMetadata(metadata: Metadata, repository: RemoteRepository, context: String)
                : String = localRepositoryManager.getPathForRemoteMetadata(metadata, repository, context)

        override fun getRepository(): LocalRepository = localRepositoryManager.repository

        private fun findArtifactInGradleCache(artifact: Artifact): File? {
            val artifactRootDir = File(gradleCacheRoot,
                    "${artifact.groupId}/${artifact.artifactId}/${artifact.version}")

            val pomFile = artifactRootDir.walkTopDown().find {
                val classifier = if (artifact.classifier.isNullOrBlank()) "" else "${artifact.classifier}-"
                it.isFile && it.name == "${artifact.artifactId}-$classifier${artifact.version}.${artifact.extension}"
            }

            log.debug {
                "Gradle cache result for '${artifact.identifier()}:${artifact.classifier}:${artifact.extension}': " +
                        pomFile?.absolutePath
            }

            return pomFile
        }
    }
}
