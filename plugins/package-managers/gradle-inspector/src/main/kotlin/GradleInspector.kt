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

import OrtDependency
import OrtDependencyTreeModel

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.SortedSet
import java.util.concurrent.TimeUnit

import org.apache.logging.log4j.kotlin.Logging

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.internal.consumer.DefaultGradleConnector

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

/**
 * The names of Gradle (Groovy, Kotlin script) build files for a Gradle project.
 */
private val GRADLE_BUILD_FILES = listOf("build.gradle", "build.gradle.kts")

/**
 * The names of Gradle (Groovy, Kotlin script) settings files for a Gradle build.
 */
private val GRADLE_SETTINGS_FILES = listOf("settings.gradle", "settings.gradle.kts")

/**
 * The name of the option to specify the Gradle version.
 */
const val OPTION_GRADLE_VERSION = "gradleVersion"

/**
 * The [Gradle](https://gradle.org/) package manager for Java.
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *gradleVersion*: The version of Gradle to use when analyzing projects. Defaults to the version defined in the
 *   Gradle wrapper properties.
 */
class GradleInspector(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {
    private companion object : Logging

    class Factory : AbstractPackageManagerFactory<GradleInspector>("GradleInspector", isEnabledByDefault = false) {
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

    private fun extractInitScript(): File {
        fun extractResource(name: String, target: File): File {
            target.outputStream().use { outputStream ->
                val resource = checkNotNull(javaClass.getResource(name)) {
                    "Resource '$name' not found."
                }

                logger.debug { "Extracting resource '${resource.path.substringAfterLast('/')}' to '$target'..." }

                resource.openStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            return target
        }

        val pluginJar = extractResource("/gradle-plugin.jar", createOrtTempFile(prefix = "plugin", suffix = ".jar"))

        val initScriptText = javaClass.getResource("/init.gradle.template").readText()
            .replace("<REPLACE_PLUGIN_JAR>", pluginJar.invariantSeparatorsPath)

        val initScript = createOrtTempFile("init", ".gradle")

        logger.debug { "Extracting Gradle init script to '$initScript'..." }

        return initScript.apply { writeText(initScriptText) }
    }

    private fun GradleConnector.getOrtDependencyTreeModel(projectDir: File): OrtDependencyTreeModel =
        forProjectDirectory(projectDir).connect().use { connection ->
            val initScriptFile = extractInitScript()

            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()

            // In order to debug the plugin, pass the "-Dorg.gradle.debug=true" option to the JVM running ORT. This will
            // then block execution of the plugin until a remote debug session is attached to port 5005 (by default),
            // also see https://docs.gradle.org/current/userguide/troubleshooting.html#sec:troubleshooting_build_logic.
            val model = connection.model(OrtDependencyTreeModel::class.java)
                .addProgressListener(ProgressListener { logger.debug { it.displayName } })
                .setStandardOutput(stdout)
                .setStandardError(stderr)
                .withArguments("--init-script", initScriptFile.path)
                .get()

            if (stdout.size() > 0) {
                logger.debug {
                    "Analyzing the project in '$projectDir' produced the following standard output:\n" +
                            stdout.toString().prependIndent("\t")
                }
            }

            if (stderr.size() > 0) {
                logger.warn {
                    "Analyzing the project in '$projectDir' produced the following error output:\n" +
                            stderr.toString().prependIndent("\t")
                }
            }

            if (!initScriptFile.delete()) {
                logger.warn { "Init script file '$initScriptFile' could not be deleted." }
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

        val dependencyTreeModel = gradleConnector.getOrtDependencyTreeModel(projectDir)

        val issues = mutableListOf<Issue>()

        dependencyTreeModel.errors.distinct().mapTo(issues) {
            createAndLogIssue(source = managerName, message = it, severity = Severity.ERROR)
        }

        dependencyTreeModel.warnings.distinct().mapTo(issues) {
            createAndLogIssue(source = managerName, message = it, severity = Severity.WARNING)
        }

        val projectId = Identifier(
            type = managerName,
            namespace = dependencyTreeModel.group,
            name = dependencyTreeModel.name,
            version = dependencyTreeModel.version
        )

        val packageDependencies = mutableSetOf<OrtDependency>()

        val scopes = dependencyTreeModel.configurations.filterNot {
            excludes.isScopeExcluded(it.name)
        }.mapTo(sortedSetOf()) {
            Scope(name = it.name, dependencies = it.dependencies.toPackageRefs(packageDependencies))
        }

        val project = Project(
            id = projectId,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = emptySet(),
            declaredLicenses = emptySet(),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = processProjectVcs(definitionFile.parentFile),
            homepageUrl = "",
            scopeDependencies = scopes
        )

        val packages = packageDependencies.associateBy {
            // Deduplicate OrtDependency serialization proxy objects by Identifier.
            Identifier("Maven", it.groupId, it.artifactId, it.version)
        }.mapNotNullTo(mutableSetOf()) { (id, _) ->
            Package.EMPTY.copy(id = id)
        }

        val result = ProjectAnalyzerResult(project, packages, issues)
        return listOf(result)
    }
}

/**
 * Recursively convert a collection of [OrtDependency] objects to a set of [PackageReference] objects for use in [Scope]
 * while flattening all dependencies into the [packageDependencies] collection.
 */
private fun Collection<OrtDependency>.toPackageRefs(
    packageDependencies: MutableCollection<OrtDependency>
): SortedSet<PackageReference> =
    mapTo(sortedSetOf()) { dep ->
        val (id, linkage) = if (dep.localPath != null) {
            val id = Identifier("Gradle", dep.groupId, dep.artifactId, dep.version)
            id to PackageLinkage.PROJECT_DYNAMIC
        } else {
            packageDependencies += dep

            val id = Identifier("Maven", dep.groupId, dep.artifactId, dep.version)
            id to PackageLinkage.DYNAMIC
        }

        PackageReference(id, linkage, dep.dependencies.toPackageRefs(packageDependencies))
    }
