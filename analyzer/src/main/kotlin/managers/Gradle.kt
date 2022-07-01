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

import DependencyTreeModel

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.repository.WorkspaceRepository

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.DefaultGradleConnector

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.managers.utils.GradleDependencyHandler
import org.ossreviewtoolkit.analyzer.managers.utils.MavenSupport
import org.ossreviewtoolkit.analyzer.managers.utils.identifier
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.temporaryProperties
import org.ossreviewtoolkit.utils.ort.createOrtTempFile
import org.ossreviewtoolkit.utils.ort.log

private val GRADLE_USER_HOME = Os.env["GRADLE_USER_HOME"]?.let { File(it) } ?: Os.userHomeDirectory.resolve(".gradle")

private val GRADLE_BUILD_FILES = listOf("build.gradle", "build.gradle.kts")
private val GRADLE_SETTINGS_FILES = listOf("settings.gradle", "settings.gradle.kts")

private const val JAVA_MAX_HEAP_SIZE_OPTION = "-Xmx"
private const val JAVA_MAX_HEAP_SIZE_VALUE = "8g"

/**
 * The [Gradle](https://gradle.org/) package manager for Java.
 */
class Gradle(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration,
    private val gradleVersion: String? = null
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Gradle>("Gradle") {
        // Gradle prefers Groovy ".gradle" files over Kotlin ".gradle.kts" files, but "build" files have to come before
        // "settings" files as we should consider "settings" files only if the same directory does not also contain a
        // "build" file.
        override val globsForDefinitionFiles = GRADLE_BUILD_FILES + GRADLE_SETTINGS_FILES

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Gradle(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    /**
     * A workspace reader that is backed by the local Gradle artifact cache.
     */
    private class GradleCacheReader : WorkspaceReader {
        private val workspaceRepository = WorkspaceRepository("gradleCache")
        private val gradleCacheRoot = GRADLE_USER_HOME.resolve("caches/modules-2/files-2.1")

        override fun findArtifact(artifact: Artifact): File? {
            val artifactRootDir = gradleCacheRoot.resolve(
                "${artifact.groupId}/${artifact.artifactId}/${artifact.version}"
            )

            val artifactFiles = artifactRootDir.walk().filter {
                val classifier = if (artifact.classifier.isNullOrBlank()) "" else "${artifact.classifier}-"
                it.isFile && it.name == "${artifact.artifactId}-$classifier${artifact.version}.${artifact.extension}"
            }.sortedByDescending {
                it.lastModified()
            }.toList()

            val artifactCoordinate = "${artifact.identifier()}:${artifact.classifier}:${artifact.extension}"

            if (artifactFiles.size > 1) {
                log.debug { "Multiple Gradle cache entries matching '$artifactCoordinate' found: $artifactFiles" }
            }

            // Return the most recent file, if any, as that is most likely the correct one, e.g. in case of a silent
            // update of an already published artifact.
            return artifactFiles.firstOrNull()?.also { artifactFile ->
                log.debug { "Using Gradle cache entry at '$artifactFile' for artifact '$artifactCoordinate'." }
            }
        }

        override fun findVersions(artifact: Artifact) =
            // Do not resolve versions of already locally available artifacts. This also ensures version resolution
            // was done by Gradle.
            if (findArtifact(artifact)?.isFile == true) listOf(artifact.version) else emptyList()

        override fun getRepository() = workspaceRepository
    }

    private val maven = MavenSupport(GradleCacheReader())
    private val dependencyHandler = GradleDependencyHandler(managerName, maven)
    private val graphBuilder = DependencyGraphBuilder(dependencyHandler)

    // The path to the root project. In a single-project, just points to the project path.
    private lateinit var rootProjectDir: File

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val gradleProperties = mutableListOf<Pair<String, String>>()

        val projectDir = definitionFile.parentFile
        val isRootProject = GRADLE_SETTINGS_FILES.any { projectDir.resolve(it).isFile }

        // TODO: Improve the logic to work for independent projects that are stored in a directory below another
        //       independent project.
        val isIndependentProject = !this::rootProjectDir.isInitialized || !projectDir.startsWith(rootProjectDir)

        // Do not reset the root project directory for subprojects.
        if (isRootProject || isIndependentProject) rootProjectDir = projectDir

        val userPropertiesFile = GRADLE_USER_HOME.resolve("gradle.properties")
        if (userPropertiesFile.isFile) {
            userPropertiesFile.inputStream().use {
                val properties = Properties().apply { load(it) }

                properties.mapNotNullTo(gradleProperties) { (key, value) ->
                    ((key as String) to (value as String)).takeUnless { key.startsWith("systemProp.") }
                }
            }
        }

        val gradleConnector = GradleConnector.newConnector()

        if (gradleVersion != null) {
            gradleConnector.useGradleVersion(gradleVersion)
        }

        if (gradleConnector is DefaultGradleConnector) {
            // Note that the Gradle Tooling API always uses the Gradle daemon, see
            // https://docs.gradle.org/current/userguide/third_party_integration.html#sec:embedding_daemon.
            gradleConnector.daemonMaxIdleTime(10, TimeUnit.SECONDS)
        }

        // Gradle's default maximum heap is 512 MiB which is too low for bigger projects,
        // see https://docs.gradle.org/current/userguide/build_environment.html#sec:configuring_jvm_memory.
        // Set the value to empirically determined 8 GiB if no value is set in "~/.gradle/gradle.properties".
        val jvmArgs = gradleProperties.find { (key, _) ->
            key == "org.gradle.jvmargs"
        }?.second?.split(' ').orEmpty().toMutableList()

        if (jvmArgs.none { it.contains(JAVA_MAX_HEAP_SIZE_OPTION, ignoreCase = true) }) {
            jvmArgs += "$JAVA_MAX_HEAP_SIZE_OPTION$JAVA_MAX_HEAP_SIZE_VALUE"
        }

        val gradleConnection = gradleConnector.forProjectDirectory(projectDir).connect()

        return temporaryProperties("user.dir" to rootProjectDir.path) {
            gradleConnection.use { connection ->
                val initScriptFile = createOrtTempFile("init", ".gradle")
                initScriptFile.writeBytes(javaClass.getResource("/scripts/init.gradle").readBytes())

                val stdout = ByteArrayOutputStream()
                val stderr = ByteArrayOutputStream()

                val dependencyTreeModel = connection
                    .model(DependencyTreeModel::class.java)
                    .addJvmArguments(jvmArgs)
                    .setStandardOutput(stdout)
                    .setStandardError(stderr)
                    .withArguments("-Duser.home=${Os.userHomeDirectory}", "--init-script", initScriptFile.path)
                    .get()

                if (stdout.size() > 0) {
                    log.debug {
                        "Analyzing the project in '$projectDir' produced the following standard output:\n" +
                                stdout.toString().prependIndent("\t")
                    }
                }

                if (stderr.size() > 0) {
                    log.warn {
                        "Analyzing the project in '$projectDir' produced the following error output:\n" +
                                stderr.toString().prependIndent("\t")
                    }
                }

                if (!initScriptFile.delete()) {
                    log.warn { "Init script file '$initScriptFile' could not be deleted." }
                }

                val repositories = dependencyTreeModel.repositories.map {
                    // TODO: Also handle authentication and snapshot policy.
                    RemoteRepository.Builder(it, "default", it).build()
                }

                dependencyHandler.repositories = repositories

                log.debug {
                    val projectName = dependencyTreeModel.name
                    "The Gradle project '$projectName' uses the following Maven repositories: $repositories"
                }

                val projectId = Identifier(
                    type = managerName,
                    namespace = dependencyTreeModel.group,
                    name = dependencyTreeModel.name,
                    version = dependencyTreeModel.version
                )

                dependencyTreeModel.configurations.forEach { configuration ->
                    configuration.dependencies.forEach { dependency ->
                        graphBuilder.addDependency(
                            DependencyGraph.qualifyScope(projectId, configuration.name),
                            dependency
                        )
                    }
                }

                val project = Project(
                    id = projectId,
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    authors = sortedSetOf(),
                    declaredLicenses = sortedSetOf(),
                    vcs = VcsInfo.EMPTY,
                    vcsProcessed = processProjectVcs(definitionFile.parentFile),
                    homepageUrl = "",
                    scopeNames = graphBuilder.scopesFor(projectId)
                )

                val issues = mutableListOf<OrtIssue>()

                dependencyTreeModel.errors.mapTo(issues) {
                    createAndLogIssue(source = managerName, message = it, severity = Severity.ERROR)
                }

                dependencyTreeModel.warnings.mapTo(issues) {
                    createAndLogIssue(source = managerName, message = it, severity = Severity.WARNING)
                }

                listOf(ProjectAnalyzerResult(project, sortedSetOf(), issues))
            }
        }
    }
}
