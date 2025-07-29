/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.gradle

import OrtDependencyTreeModel
import OrtRepository

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

import org.apache.logging.log4j.kotlin.logger

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.repository.WorkspaceRepository
import org.eclipse.aether.util.repository.AuthenticationBuilder

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.model.build.BuildEnvironment

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.MavenSupport
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.identifier
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.temporaryProperties
import org.ossreviewtoolkit.utils.common.unquote
import org.ossreviewtoolkit.utils.ort.JavaBootstrapper
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

import org.semver4j.Semver

private val GRADLE_USER_HOME = Os.env["GRADLE_USER_HOME"]?.let { File(it) } ?: Os.userHomeDirectory.resolve(".gradle")

private val GRADLE_BUILD_FILES = listOf("build.gradle", "build.gradle.kts")
private val GRADLE_SETTINGS_FILES = listOf("settings.gradle", "settings.gradle.kts")

private const val JAVA_MAX_HEAP_SIZE_OPTION = "-Xmx"
private const val JAVA_MAX_HEAP_SIZE_VALUE = "8g"

data class GradleConfig(
    /**
     * The version of Gradle to use when analyzing projects. Defaults to the version defined in the Gradle wrapper
     * properties.
     */
    val gradleVersion: String?,

    /**
     * The version of Java to use when analyzing projects. By default, the same Java version as for ORT itself it used.
     * Overrides `javaHome` if both are specified.
     */
    val javaVersion: String?,

    /**
     * The directory of the Java home to use when analyzing projects. By default, the same Java home as for ORT itself
     * is used.
     */
    val javaHome: String?
)

/**
 * The [Gradle](https://gradle.org/) package manager for Java. Also see the
 * [compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html).
 */
@OrtPlugin(
    displayName = "Gradle",
    description = "The Gradle package manager for Java.",
    factory = PackageManagerFactory::class
)
class Gradle(
    override val descriptor: PluginDescriptor = GradleFactory.descriptor,
    private val config: GradleConfig
) : PackageManager("Gradle") {
    // Gradle prefers Groovy ".gradle" files over Kotlin ".gradle.kts" files, but "build" files have to come before
    // "settings" files as we should consider "settings" files only if the same directory does not also contain a
    // "build" file.
    override val globsForDefinitionFiles = GRADLE_BUILD_FILES + GRADLE_SETTINGS_FILES

    /**
     * A workspace reader that is backed by the local Gradle artifact cache.
     */
    private class GradleCacheReader : WorkspaceReader {
        private val workspaceRepository = WorkspaceRepository("gradle/remote-artifacts")
        private val gradleCacheRoot = GRADLE_USER_HOME / "caches" / "modules-2" / "files-2.1"

        override fun findArtifact(artifact: Artifact): File? {
            val artifactRootDir = gradleCacheRoot / artifact.groupId / artifact.artifactId / artifact.version

            val artifactFiles = artifactRootDir.walk().filter {
                val classifier = if (artifact.classifier.isNullOrBlank()) "" else "${artifact.classifier}-"
                it.isFile && it.name == "${artifact.artifactId}-$classifier${artifact.version}.${artifact.extension}"
            }.sortedByDescending {
                it.lastModified()
            }.toList()

            val artifactCoordinate = "${artifact.identifier()}:${artifact.classifier}:${artifact.extension}"

            if (artifactFiles.size > 1) {
                logger.debug { "Multiple Gradle cache entries matching '$artifactCoordinate' found: $artifactFiles" }
            }

            // Return the most recent file, if any, as that is most likely the correct one, e.g. in case of a silent
            // update of an already published artifact.
            return artifactFiles.firstOrNull()?.also { artifactFile ->
                logger.debug { "Using Gradle cache entry at '$artifactFile' for artifact '$artifactCoordinate'." }
            }
        }

        override fun findVersions(artifact: Artifact) =
            // Do not resolve versions of already locally available artifacts. This also ensures version resolution
            // was done by Gradle.
            if (findArtifact(artifact)?.isFile == true) listOf(artifact.version) else emptyList()

        override fun getRepository() = workspaceRepository
    }

    private val mavenSupport = MavenSupport(GradleCacheReader())
    private val dependencyHandler = GradleDependencyHandler(projectType, mavenSupport)
    private val graphBuilder = DependencyGraphBuilder(dependencyHandler)

    // The path to the root project. In a single-project, just points to the project path.
    private lateinit var rootProjectDir: File

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val projectDir = definitionFile.parentFile
        val isRootProject = GRADLE_SETTINGS_FILES.any { projectDir.resolve(it).isFile }

        // TODO: Improve the logic to work for independent projects that are stored in a directory below another
        //       independent project.
        val isIndependentProject = !this::rootProjectDir.isInitialized || !projectDir.startsWith(rootProjectDir)

        // Do not reset the root project directory for subprojects.
        if (isRootProject || isIndependentProject) rootProjectDir = projectDir

        val gradleConnector = GradleConnector.newConnector()

        if (config.gradleVersion != null) {
            gradleConnector.useGradleVersion(config.gradleVersion)
        }

        if (gradleConnector is DefaultGradleConnector) {
            // Note that the Gradle Tooling API always uses the Gradle daemon, see
            // https://docs.gradle.org/current/userguide/third_party_integration.html#sec:embedding_daemon.
            gradleConnector.daemonMaxIdleTime(10, TimeUnit.SECONDS)
        }

        // Gradle's default maximum heap is 512 MiB which is too low for bigger projects,
        // see https://docs.gradle.org/current/userguide/build_environment.html#sec:configuring_jvm_memory.
        // Set the value to empirically determined 8 GiB if no value is set in "~/.gradle/gradle.properties".
        val jvmArgs = getGradleProperties()["org.gradle.jvmargs"].orEmpty()
            .replace("MaxPermSize", "MaxMetaspaceSize") // Replace a deprecated JVM argument.
            .splitOnWhitespace()
            .mapTo(mutableListOf()) { it.unquote() }

        if (jvmArgs.none { it.contains(JAVA_MAX_HEAP_SIZE_OPTION, ignoreCase = true) }) {
            jvmArgs += "$JAVA_MAX_HEAP_SIZE_OPTION$JAVA_MAX_HEAP_SIZE_VALUE"
        }

        val gradleConnection = gradleConnector.forProjectDirectory(projectDir).connect()

        return temporaryProperties("user.dir" to rootProjectDir.path) {
            gradleConnection.use { connection ->
                val initScriptFile = createOrtTempFile("init", ".gradle")
                initScriptFile.writeBytes(javaClass.getResource("/init.gradle").readBytes())

                val stdout = ByteArrayOutputStream()
                val stderr = ByteArrayOutputStream()

                val environment = connection.model(BuildEnvironment::class.java).get()
                val buildGradleVersion = Semver.coerce(environment.gradle.gradleVersion)

                logger.info { "The project at '$projectDir' uses Gradle version $buildGradleVersion." }

                val issues = mutableListOf<Issue>()

                val dependencyTreeModel = connection.model(OrtDependencyTreeModel::class.java)
                    .apply {
                        // Work around https://github.com/gradle/gradle/issues/28464.
                        if (logger.delegate.isDebugEnabled && buildGradleVersion?.isEqualTo("8.5.0") != true) {
                            addProgressListener(ProgressListener { logger.debug(it.displayName) })
                        }

                        val javaHome = config.javaVersion
                            ?.takeUnless { JavaBootstrapper.isRunningOnJdk(it) }
                            ?.let {
                                JavaBootstrapper.installJdk("TEMURIN", it).onFailure { e ->
                                    issues += createAndLogIssue(e.collectMessages())
                                }.getOrNull()
                            } ?: config.javaHome?.let { File(it) }

                        javaHome?.also {
                            logger.info { "Setting Java home for project analysis to '$it'." }
                            setJavaHome(it)
                        }
                    }
                    .addJvmArguments(jvmArgs)
                    .setStandardOutput(stdout)
                    .setStandardError(stderr)
                    .withArguments("-Duser.home=${Os.userHomeDirectory}", "--init-script", initScriptFile.path)
                    .get()

                if (stdout.size() > 0) {
                    logger.debug {
                        "Analyzing the project in '$projectDir' produced the following standard output:\n" +
                            stdout.toString().prependIndent("\t")
                    }
                }

                if (stderr.size() > 0) {
                    logger.debug {
                        "Analyzing the project in '$projectDir' produced the following error output:\n" +
                            stderr.toString().prependIndent("\t")
                    }
                }

                initScriptFile.parentFile.safeDeleteRecursively()

                val repositories = dependencyTreeModel.repositories.map { it.toRemoteRepository() }

                dependencyHandler.repositories = repositories

                logger.debug {
                    val projectName = dependencyTreeModel.name
                    "The Gradle project '$projectName' uses the following Maven repositories: $repositories"
                }

                val projectId = Identifier(
                    type = projectType,
                    namespace = dependencyTreeModel.group,
                    name = dependencyTreeModel.name,
                    version = dependencyTreeModel.version
                )

                dependencyTreeModel.configurations.filterNot {
                    excludes.isScopeExcluded(it.name)
                }.forEach { configuration ->
                    graphBuilder.addDependencies(projectId, configuration.name, configuration.dependencies)
                }

                val project = Project(
                    id = projectId,
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    authors = emptySet(),
                    declaredLicenses = emptySet(),
                    vcs = VcsInfo.EMPTY,
                    vcsProcessed = processProjectVcs(definitionFile.parentFile),
                    homepageUrl = "",
                    scopeNames = graphBuilder.scopesFor(projectId)
                )

                dependencyTreeModel.errors.mapTo(issues) {
                    createAndLogIssue(it, Severity.ERROR)
                }

                dependencyTreeModel.warnings.mapTo(issues) {
                    createAndLogIssue(it, Severity.WARNING)
                }

                listOf(ProjectAnalyzerResult(project, emptySet(), issues))
            }
        }
    }

    override fun afterResolution(analysisRoot: File, definitionFiles: List<File>) {
        mavenSupport.close()
    }
}

private fun getGradleProperties(): Map<String, String> =
    GRADLE_USER_HOME.resolve("gradle.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use { Properties().apply { load(it) } }
        ?.mapNotNull { (key, value) ->
            key.toString().takeUnless { it.startsWith("systemProp.") }?.let { it to value.toString() }
        }
        ?.toMap()
        .orEmpty()

/**
 * Convert this [OrtRepository] to a [RemoteRepository] taking the known properties into account.
 * TODO: Also handle snapshot policy.
 */
private fun OrtRepository.toRemoteRepository(): RemoteRepository =
    RemoteRepository.Builder(url, "default", url).apply {
        if (username != null) {
            setAuthentication(
                AuthenticationBuilder().apply {
                    addUsername(username)
                    password?.also(::addPassword)
                }.build()
            )
        }
    }.build()
