/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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
import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.identifier
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Error
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.utils.collectMessages
import com.here.ort.utils.log
import com.here.ort.utils.showStackTrace

import java.io.File

import org.apache.maven.project.ProjectBuildingException

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.repository.WorkspaceRepository

import org.gradle.tooling.GradleConnector

/**
 * The Gradle package manager for Java, see https://gradle.org/.
 */
class Gradle(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Gradle>() {
        override val globsForDefinitionFiles = listOf("build.gradle", "settings.gradle")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                Gradle(analyzerConfig, repoConfig)
    }

    /**
     * A workspace reader that is backed by the local Gradle artifact cache.
     */
    private class GradleCacheReader : WorkspaceReader {
        private val workspaceRepository = WorkspaceRepository("gradleCache")
        private val gradleCacheRoot = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")

        override fun findArtifact(artifact: Artifact): File? {
            val artifactRootDir = File(gradleCacheRoot,
                    "${artifact.groupId}/${artifact.artifactId}/${artifact.version}")

            val artifactFile = artifactRootDir.walkTopDown().find {
                val classifier = if (artifact.classifier.isNullOrBlank()) "" else "${artifact.classifier}-"
                it.isFile && it.name == "${artifact.artifactId}-$classifier${artifact.version}.${artifact.extension}"
            }

            log.debug {
                "Gradle cache result for '${artifact.identifier()}:${artifact.classifier}:${artifact.extension}': " +
                        artifactFile?.invariantSeparatorsPath
            }

            return artifactFile
        }

        override fun findVersions(artifact: Artifact) =
                // Do not resolve versions of already locally available artifacts. This also ensures version resolution
                // was done by Gradle.
                if (findArtifact(artifact)?.isFile == true) listOf(artifact.version) else emptyList()

        override fun getRepository() = workspaceRepository
    }

    private val maven = MavenSupport(GradleCacheReader())

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val gradleConnection = GradleConnector
                .newConnector()
                .forProjectDirectory(definitionFile.parentFile)
                .connect()

        gradleConnection.use { connection ->
            val initScriptFile = File.createTempFile("init", ".gradle")
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

                Scope(configuration.name, dependencies.toSortedSet())
            }

            val project = Project(
                    id = Identifier(
                            provider = toString(),
                            namespace = dependencyTreeModel.group,
                            name = dependencyTreeModel.name,
                            version = dependencyTreeModel.version
                    ),
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    declaredLicenses = sortedSetOf(),
                    vcs = VcsInfo.EMPTY,
                    vcsProcessed = processProjectVcs(definitionFile.parentFile),
                    homepageUrl = "",
                    scopes = scopes.toSortedSet()
            )

            val errors = dependencyTreeModel.errors.map {
                Error(source = toString(), message = it)
            }

            return ProjectAnalyzerResult(project, packages.values.map { it.toCuratedPackage() }.toSortedSet(), errors)
        }
    }

    private fun parseDependency(dependency: Dependency, packages: MutableMap<String, Package>,
                                repositories: List<RemoteRepository>): PackageReference {
        val errors = dependency.error?.let { mutableListOf(Error(source = toString(), message = it)) }
                ?: mutableListOf()

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
                        e.showStackTrace()

                        log.error {
                            "Could not get package information for dependency '$identifier': ${e.message}"
                        }

                        errors += e.collectMessages().map { Error(source = toString(), message = it) }

                        rawPackage
                    }
                } else {
                    rawPackage
                }

                pkg.copy(vcsProcessed = dependency.localPath?.let { processProjectVcs(File(it), pkg.vcs) }
                        ?: processPackageVcs(pkg.vcs))
            }
        }

        val transitiveDependencies = dependency.dependencies.map { parseDependency(it, packages, repositories) }
        return PackageReference(Identifier("Maven", dependency.groupId, dependency.artifactId,
                dependency.version), transitiveDependencies.toSortedSet(), errors)
    }
}
