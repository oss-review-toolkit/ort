/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.gradleinspector

import OrtDependencyTreeModel

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

import org.apache.logging.log4j.kotlin.logger

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.model.build.BuildEnvironment

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.unquote
import org.ossreviewtoolkit.utils.ort.JavaBootstrapper
import org.ossreviewtoolkit.utils.ort.ortToolsDirectory

import org.semver4j.Semver

/**
 * The names of Gradle (Groovy, Kotlin script) build files for a Gradle project.
 */
private val GRADLE_BUILD_FILES = listOf("build.gradle", "build.gradle.kts")

/**
 * The names of Gradle (Groovy, Kotlin script) settings files for a Gradle build.
 */
private val GRADLE_SETTINGS_FILES = listOf("settings.gradle", "settings.gradle.kts")

/**
 * The Gradle user home directory.
 */
private val GRADLE_USER_HOME = Os.env["GRADLE_USER_HOME"]?.let { File(it) } ?: Os.userHomeDirectory.resolve(".gradle")

/**
 * The name of the option to specify the Gradle version.
 */
const val OPTION_GRADLE_VERSION = "gradleVersion"

/**
 * The name of the option to specify the Java version to use.
 */
const val OPTION_JAVA_VERSION = "javaVersion"

/**
 * The name of the option to specify the Java home to use.
 */
const val OPTION_JAVA_HOME = "javaHome"

/**
 * The [Gradle](https://gradle.org/) package manager for Java. Also see the
 * [compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html).
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *gradleVersion*: The version of Gradle to use when analyzing projects. Defaults to the version defined in the
 *   Gradle wrapper properties.
 * - *javaVersion*: The version of Java to use when analyzing projects. By default, the same Java version as for ORT
 *   itself it used. Overrides `javaHome` if both are specified.
 * - *javaHome*: The directory of the Java home to use when analyzing projects. By default, the same Java home as for
 *   ORT itself is used.
 */
class GradleInspector(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<GradleInspector>("GradleInspector") {
        // Gradle prefers Groovy ".gradle" files over Kotlin ".gradle.kts" files, but "build" files have to come before
        // "settings" files as we should consider "settings" files only if the same directory does not also contain a
        // "build" file.
        override val globsForDefinitionFiles = GRADLE_BUILD_FILES + GRADLE_SETTINGS_FILES

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = GradleInspector(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val graphBuilder = DependencyGraphBuilder(GradleDependencyHandler(managerName))
    private val initScriptFile by lazy { extractInitScript() }

    private fun extractInitScript(): File {
        fun extractResource(name: String, target: File) =
            target.apply {
                val resource = checkNotNull(GradleInspector::class.java.getResource(name)) {
                    "Resource '$name' not found."
                }

                logger.debug { "Extracting resource '${resource.path.substringAfterLast('/')}' to '$target'..." }

                resource.openStream().use { inputStream ->
                    outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }

        val toolsDir = ortToolsDirectory.resolve(managerName).apply { safeMkdirs() }
        val pluginJar = extractResource("/gradle-plugin.jar", toolsDir.resolve("gradle-plugin.jar"))

        val initScriptText = javaClass.getResource("/template.init.gradle").readText()
            .replace("<REPLACE_PLUGIN_JAR>", pluginJar.invariantSeparatorsPath)

        val initScript = toolsDir.resolve("init.gradle")

        logger.debug { "Extracting Gradle init script to '$initScript'..." }

        return initScript.apply { writeText(initScriptText) }
    }

    private fun GradleConnector.getOrtDependencyTreeModel(
        projectDir: File,
        issues: MutableList<Issue>
    ): OrtDependencyTreeModel =
        forProjectDirectory(projectDir).connect().use { connection ->
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()

            val gradleProperties = readGradleProperties(GRADLE_USER_HOME) + readGradleProperties(projectDir)
            val jvmArgs = gradleProperties["org.gradle.jvmargs"].orEmpty()
                .replace("MaxPermSize", "MaxMetaspaceSize") // Replace a deprecated JVM argument.
                .splitOnWhitespace()
                .map { it.unquote() }

            val environment = connection.model(BuildEnvironment::class.java).get()
            val buildGradleVersion = Semver.coerce(environment.gradle.gradleVersion)

            logger.info { "The project at '$projectDir' uses Gradle version $buildGradleVersion." }

            // In order to debug the plugin, pass the "-Dorg.gradle.debug=true" option to the JVM running ORT. This will
            // then block execution of the plugin until a remote debug session is attached to port 5005 (by default),
            // also see https://docs.gradle.org/current/userguide/troubleshooting.html#sec:troubleshooting_build_logic.
            val model = connection.model(OrtDependencyTreeModel::class.java)
                .apply {
                    // Work around https://github.com/gradle/gradle/issues/28464.
                    if (logger.delegate.isDebugEnabled && buildGradleVersion?.isEqualTo("8.5.0") != true) {
                        addProgressListener(ProgressListener { logger.debug(it.displayName) })
                    }

                    val javaHome = options[OPTION_JAVA_VERSION]
                        ?.takeUnless { JavaBootstrapper.isRunningOnJdk(it) }
                        ?.let {
                            JavaBootstrapper.installJdk("TEMURIN", it)
                                .onFailure { e -> issues += createAndLogIssue(managerName, e.collectMessages()) }
                                .getOrNull()
                        } ?: options[OPTION_JAVA_HOME]?.let { File(it) }

                    javaHome?.also {
                        logger.info { "Setting Java home for project analysis to '$it'." }
                        setJavaHome(it)
                    }
                }
                .setJvmArguments(jvmArgs)
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

            model
        }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val projectDir = definitionFile.parentFile

        val gradleConnector = GradleConnector.newConnector()

        val gradleVersion = options[OPTION_GRADLE_VERSION]
        if (gradleVersion != null) {
            gradleConnector.useGradleVersion(gradleVersion)
        }

        if (gradleConnector is DefaultGradleConnector) {
            // Note that the Gradle Tooling API always uses the Gradle daemon, see
            // https://docs.gradle.org/current/userguide/third_party_integration.html#sec:embedding_daemon.
            gradleConnector.daemonMaxIdleTime(1, TimeUnit.SECONDS)
        }

        val issues = mutableListOf<Issue>()
        val dependencyTreeModel = gradleConnector.getOrtDependencyTreeModel(projectDir, issues)

        dependencyTreeModel.errors.distinct().mapTo(issues) {
            createAndLogIssue(source = managerName, message = it, severity = Severity.ERROR)
        }

        dependencyTreeModel.warnings.distinct().mapTo(issues) {
            createAndLogIssue(source = managerName, message = it, severity = Severity.WARNING)
        }

        val projectId = Identifier(
            type = "Gradle",
            namespace = dependencyTreeModel.group,
            name = dependencyTreeModel.name,
            version = dependencyTreeModel.version
        )

        dependencyTreeModel.configurations.filterNot {
            excludes.isScopeExcluded(it.name)
        }.forEach { configuration ->
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
            authors = emptySet(),
            declaredLicenses = emptySet(),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = processProjectVcs(definitionFile.parentFile),
            homepageUrl = "",
            scopeNames = graphBuilder.scopesFor(projectId)
        )

        val result = ProjectAnalyzerResult(project, emptySet(), issues)
        return listOf(result)
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
}

/**
 * Read the `gradle.properties` file in [projectDir] or in any of its parent directories, and return the contained
 * properties as a map.
 */
private fun readGradleProperties(projectDir: File): Map<String, String> {
    val gradleProperties = mutableListOf<Pair<String, String>>()

    var currentDir: File? = projectDir
    do {
        val propertiesFile = currentDir?.resolve("gradle.properties")

        if (propertiesFile?.isFile == true) {
            propertiesFile.inputStream().use {
                val properties = Properties().apply { load(it) }

                properties.mapNotNullTo(gradleProperties) { (key, value) ->
                    ((key as String) to (value as String)).takeUnless { key.startsWith("systemProp.") }
                }
            }

            break
        }

        currentDir = currentDir?.parentFile
    } while (currentDir != null)

    return gradleProperties.toMap()
}
