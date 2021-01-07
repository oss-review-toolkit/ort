/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer.managers

import Dependency
import DependencyTreeModel

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

import org.apache.maven.project.ProjectBuildingException

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.repository.WorkspaceRepository

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.DefaultGradleConnector

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.managers.utils.MavenSupport
import org.ossreviewtoolkit.analyzer.managers.utils.identifier
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace
import org.ossreviewtoolkit.utils.temporaryProperties

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
        override val globsForDefinitionFiles = listOf(
            "build.gradle", "build.gradle.kts",
            "settings.gradle", "settings.gradle.kts"
        )

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
        private val gradleCacheRoot = Os.userHomeDirectory.resolve(".gradle/caches/modules-2/files-2.1")

        override fun findArtifact(artifact: Artifact): File? {
            val artifactRootDir = File(
                gradleCacheRoot,
                "${artifact.groupId}/${artifact.artifactId}/${artifact.version}"
            )

            val artifactFile = artifactRootDir.walk().find {
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

    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val gradleSystemProperties = mutableListOf<Pair<String, String>>()
        val gradleProperties = mutableListOf<Pair<String, String>>()

        // Usually, the Gradle wrapper's Java code handles applying system properties defined in a Gradle properties
        // file. But as we use the Gradle Tooling API instead of the wrapper to start the build, we need to manually
        // load any system properties from a Gradle properties file and set them in the process that uses the Tooling
        // API. A typical use case for this is to apply proxy settings so that the Gradle distribution used by the build
        // can be downloaded behind a proxy, see https://github.com/gradle/gradle/issues/6825#issuecomment-502720562.
        // For simplicity, limit the search for system properties to the current user's Gradle properties file for now.
        val gradlePropertiesFile = Os.userHomeDirectory.resolve(".gradle/gradle.properties")
        if (gradlePropertiesFile.isFile) {
            gradlePropertiesFile.inputStream().use {
                val properties = Properties().apply { load(it) }

                properties.mapNotNullTo(gradleSystemProperties) { (key, value) ->
                    val systemPropKey = (key as String).removePrefix("systemProp.")
                    (systemPropKey to (value as String)).takeIf { systemPropKey != key }
                }

                properties.mapNotNullTo(gradleProperties) { (key, value) ->
                    ((key as String) to (value as String)).takeUnless { key.startsWith("systemProp.") }
                }
            }

            log.debug {
                "Will apply the following system properties defined in file '$gradlePropertiesFile':" +
                        gradleSystemProperties.joinToString(separator = "\n\t", prefix = "\n\t") {
                            "${it.first} = ${it.second}"
                        }
            }
        } else {
            log.debug {
                "Not applying any system properties as no '$gradlePropertiesFile' file was found."
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
        }?.second?.split(" ").orEmpty().toMutableList()

        if (jvmArgs.none { it.contains("-xmx", ignoreCase = true) }) {
            jvmArgs += "-Xmx8g"
        }

        val projectDir = definitionFile.parentFile
        val gradleConnection = gradleConnector.forProjectDirectory(projectDir).connect()

        return temporaryProperties(*gradleSystemProperties.toTypedArray()) {
            gradleConnection.use { connection ->
                val initScriptFile = File.createTempFile("init", ".gradle")
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

                log.debug {
                    val projectName = dependencyTreeModel.name
                    "The Gradle project '$projectName' uses the following Maven repositories: $repositories"
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
                        type = managerName,
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

                val issues = mutableListOf<OrtIssue>()

                dependencyTreeModel.errors.mapTo(issues) {
                    createAndLogIssue(source = managerName, message = it, severity = Severity.ERROR)
                }

                dependencyTreeModel.warnings.mapTo(issues) {
                    createAndLogIssue(source = managerName, message = it, severity = Severity.WARNING)
                }

                listOf(ProjectAnalyzerResult(project, packages.values.toSortedSet(), issues))
            }
        }
    }

    private fun parseDependency(
        dependency: Dependency, packages: MutableMap<String, Package>,
        repositories: List<RemoteRepository>
    ): PackageReference {
        val issues = mutableListOf<OrtIssue>()

        dependency.error?.let {
            issues += createAndLogIssue(
                source = managerName,
                message = it,
                severity = Severity.ERROR
            )
        }

        dependency.warning?.let {
            issues += createAndLogIssue(
                source = managerName,
                message = it,
                severity = Severity.WARNING
            )
        }

        // Only look for a package if there was no error resolving the dependency and it is no project dependency.
        if (dependency.error == null && dependency.localPath == null) {
            val identifier = "${dependency.groupId}:${dependency.artifactId}:${dependency.version}"

            packages.getOrPut(identifier) {
                try {
                    val artifact = DefaultArtifact(
                        dependency.groupId, dependency.artifactId, dependency.classifier,
                        dependency.extension, dependency.version
                    )

                    maven.parsePackage(artifact, repositories)
                } catch (e: ProjectBuildingException) {
                    e.showStackTrace()

                    issues += createAndLogIssue(
                        source = managerName,
                        message = "Could not get package information for dependency '$identifier': " +
                                e.collectMessagesAsString()
                    )

                    Package.EMPTY.copy(
                        id = Identifier(
                            type = "Maven",
                            namespace = dependency.groupId,
                            name = dependency.artifactId,
                            version = dependency.version
                        )
                    )
                }
            }
        }

        val transitiveDependencies = dependency.dependencies.map { parseDependency(it, packages, repositories) }

        return if (dependency.localPath != null) {
            val id = Identifier(managerName, dependency.groupId, dependency.artifactId, dependency.version)
            PackageReference(id, PackageLinkage.PROJECT_DYNAMIC, transitiveDependencies.toSortedSet(), issues)
        } else {
            val type = dependency.pomFile?.let { "Maven" } ?: "Unknown"
            val id = Identifier(type, dependency.groupId, dependency.artifactId, dependency.version)
            PackageReference(id, dependencies = transitiveDependencies.toSortedSet(), issues = issues)
        }
    }
}

/**
 * A helper class to construct a dependency graph when processing a Gradle project.
 */
internal class GradleDependencyGraphBuilder(
    /** The name of the dependency manager to use as type of identifiers. */
    private val managerName: String,

    /** The helper object to resolve packages via Maven. */
    private val maven: MavenSupport
) {
    /**
     * A map storing all the references to packages that have been encountered so far. This is used to de-duplicate
     * the packages in the dependency trees of the different scopes.
     */
    private val referenceMapping = mutableMapOf<String, PackageReference>()

    /** The mapping from scopes to dependencies constructed by this builder. */
    private val scopeMapping = mutableMapOf<String, Set<Identifier>>()

    /** Stores all packages encountered in the dependency tree. */
    private val resolvedPackages = mutableSetOf<Package>()

    /**
     * A set storing the packages that are direct dependencies of one of the scopes. These are the entry points into
     * the dependency graph.
     */
    private val directDependencies = mutableSetOf<PackageReference>()

    /**
     * Add the scope with the given [scopeName] to this builder. In most cases, it is not necessary to add scopes
     * explicitly, as they are recorded automatically by _addDependency()_. However, if there are scopes without
     * dependencies, this function can be used to include them into the builder result.
     */
    fun addScope(scopeName: String) {
        if (!scopeMapping.containsKey(scopeName)) {
            scopeMapping[scopeName] = emptySet()
        }
    }

    /**
     * Add the given [dependency] for the scope with the given [scopeName] to this builder. Use the provided
     * [repositories] to resolve the package if necessary.
     */
    fun addDependency(scopeName: String, dependency: Dependency, repositories: List<RemoteRepository>) {
        addDependencyToGraph(scopeName, dependency, repositories, transitive = false)
    }

    /**
     * Construct the [DependencyGraph] from the dependencies passed to this builder so far.
     */
    fun build(): DependencyGraph = DependencyGraph(directDependencies, scopeMapping)

    /**
     * Return a set with all the packages that have been encountered for the current project.
     */
    fun packages(): Set<Package> = resolvedPackages

    /**
     * Update the dependency graph by adding the given [dependency], which may be [transitive], for the scope with name
     * [scopeName]. Use the provided [repositories] to resolve the package if necessary. All the dependencies of this
     * dependency are processed recursively.
     */
    private fun addDependencyToGraph(
        scopeName: String,
        dependency: Dependency,
        repositories: List<RemoteRepository>,
        transitive: Boolean
    ): PackageReference {
        val identifier = "${dependency.groupId}:${dependency.artifactId}:${dependency.version}"
        val issues = issuesForDependency(dependency)

        val ref = referenceMapping.getOrPut(identifier) {
            updateResolvedPackages(identifier, dependency, repositories, issues)
            val transitiveDependencies = dependency.dependencies
                .map { addDependencyToGraph(scopeName, it, repositories, transitive = true) }

            val ref = if (dependency.localPath != null) {
                val id = Identifier(managerName, dependency.groupId, dependency.artifactId, dependency.version)
                PackageReference(id, PackageLinkage.PROJECT_DYNAMIC, transitiveDependencies.toSortedSet(), issues)
            } else {
                val type = dependency.pomFile?.let { "Maven" } ?: "Unknown"
                val id = Identifier(type, dependency.groupId, dependency.artifactId, dependency.version)
                PackageReference(id, dependencies = transitiveDependencies.toSortedSet(), issues = issues)
            }

            updateDirectDependencies(ref, transitive)
        }

        return updateScopeMapping(scopeName, ref, transitive)
    }

    /**
     * Return a list of issues that is initially populated with errors or warnings from the given [dependency].
     */
    private fun issuesForDependency(dependency: Dependency): MutableList<OrtIssue> {
        val issues = mutableListOf<OrtIssue>()

        dependency.error?.let {
            issues += createAndLogIssue(
                source = managerName,
                message = it,
                severity = Severity.ERROR
            )
        }

        dependency.warning?.let {
            issues += createAndLogIssue(
                source = managerName,
                message = it,
                severity = Severity.WARNING
            )
        }

        return issues
    }

    /**
     * Construct a [Package] for the given [dependency] using the [repositories] provided. Add the new package to the
     * set managed by this object. If this fails, record a corresponding message in [issues].
     */
    private fun updateResolvedPackages(
        identifier: String,
        dependency: Dependency,
        repositories: List<RemoteRepository>,
        issues: MutableList<OrtIssue>
    ) {
        // Only look for a package if there was no error resolving the dependency and it is no project dependency.
        if (dependency.error == null && dependency.localPath == null) {
            val pkg = try {
                val artifact = DefaultArtifact(
                    dependency.groupId, dependency.artifactId, dependency.classifier,
                    dependency.extension, dependency.version
                )

                maven.parsePackage(artifact, repositories)
            } catch (e: ProjectBuildingException) {
                e.showStackTrace()

                issues += createAndLogIssue(
                    source = managerName,
                    message = "Could not get package information for dependency '$identifier': " +
                            e.collectMessagesAsString()
                )

                Package.EMPTY.copy(
                    id = Identifier(
                        type = "Maven",
                        namespace = dependency.groupId,
                        name = dependency.artifactId,
                        version = dependency.version
                    )
                )
            }

            resolvedPackages += pkg
        }
    }

    /**
     * Add the given [package reference][ref] to the set of direct dependencies if it is not [transitive]. If one of
     * the direct dependencies of this package is in this set, it is removed, as it is obviously no direct dependency.
     * Because this function is called for all dependencies, all transitive dependencies are eventually removed.
     */
    private fun updateDirectDependencies(ref: PackageReference, transitive: Boolean): PackageReference {
        directDependencies.removeAll(ref.dependencies)
        if (!transitive) {
            directDependencies += ref
        }
        return ref
    }

    /**
     * Update the scope mapping for the given [scopeName] to depend on [ref], which may be a [transitive] dependency.
     * The scope mapping records all the direct dependencies of scopes.
     */
    private fun updateScopeMapping(scopeName: String, ref: PackageReference, transitive: Boolean): PackageReference {
        if (!transitive) {
            scopeMapping.compute(scopeName) { _, ids ->
                ids?.let { it + ref.id } ?: setOf(ref.id)
            }
        }

        return ref
    }
}
