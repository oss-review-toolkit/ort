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

package org.ossreviewtoolkit.analyzer

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.ServiceLoader

import kotlin.time.measureTime

import org.apache.maven.project.ProjectBuildingException

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.common.isSymbolicLink
import org.ossreviewtoolkit.utils.core.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl
import org.ossreviewtoolkit.utils.core.showStackTrace

typealias ManagedProjectFiles = Map<PackageManagerFactory, List<File>>

/**
 * A class representing a package manager that handles software dependencies. The package manager is referred to by its
 * [managerName]. The analysis of any projects and their dependencies starts in the [analysisRoot] directory using the
 * given general [analyzerConfig]. Per-repository configuration is passed in [repoConfig].
 */
abstract class PackageManager(
    val managerName: String,
    val analysisRoot: File,
    val analyzerConfig: AnalyzerConfiguration,
    val repoConfig: RepositoryConfiguration
) {
    companion object {
        private val LOADER = ServiceLoader.load(PackageManagerFactory::class.java)!!

        /**
         * The set of all available [package manager factories][PackageManagerFactory] in the classpath, sorted by name.
         */
        val ALL: Set<PackageManagerFactory> by lazy {
            LOADER.iterator().asSequence().toSortedSet(compareBy { it.managerName })
        }

        private val PACKAGE_MANAGER_DIRECTORIES = listOf(
            // Ignore intermediate build system directories.
            ".gradle",
            "node_modules",
            // Ignore resources in a standard Maven / Gradle project layout.
            "META-INF/maven",
            "src/main/resources",
            "src/test/resources",
            // Ignore virtual environments in Python.
            "lib/python2.*/dist-packages",
            "lib/python3.*/site-packages"
        )

        private val IGNORED_DIRECTORY_MATCHERS = (VCS_DIRECTORIES + PACKAGE_MANAGER_DIRECTORIES).map {
            FileSystems.getDefault().getPathMatcher("glob:**/$it")
        }

        /**
         * Recursively search the [directory] for files managed by any of the [packageManagers]. The search is performed
         * depth-first so that root project files are found before any subproject files for a specific manager.
         */
        fun findManagedFiles(directory: File, packageManagers: Set<PackageManagerFactory> = ALL): ManagedProjectFiles {
            require(directory.isDirectory) {
                "The provided path is not a directory: ${directory.absolutePath}"
            }

            val result = mutableMapOf<PackageManagerFactory, MutableList<File>>()

            Files.walkFileTree(
                directory.toPath(),
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                        if (IGNORED_DIRECTORY_MATCHERS.any { it.matches(dir) }) {
                            PackageManager.log.info {
                                "Not analyzing directory '$dir' as it is hard-coded to be ignored."
                            }

                            return FileVisitResult.SKIP_SUBTREE
                        }

                        val dirAsFile = dir.toFile()

                        // Note that although FileVisitOption.FOLLOW_LINKS is not set, this would still follow junctions
                        // on Windows, so do a better check here.
                        if (dirAsFile.isSymbolicLink()) {
                            PackageManager.log.info { "Not following symbolic link to directory '$dir'." }
                            return FileVisitResult.SKIP_SUBTREE
                        }

                        val filesInDir = dirAsFile.walk().maxDepth(1).filter {
                            it.isFile && it.length() > 0
                        }.toList()

                        packageManagers.forEach { manager ->
                            // Create a list of lists of matching files per glob.
                            val matchesPerGlob = manager.matchersForDefinitionFiles.mapNotNull { glob ->
                                // Create a list of files in the current directory that match the current glob.
                                val filesMatchingGlob = filesInDir.filter { glob.matches(it.toPath()) }
                                filesMatchingGlob.takeIf { it.isNotEmpty() }
                            }

                            if (matchesPerGlob.isNotEmpty()) {
                                // Only consider all matches for the first glob that has matches. This is because globs
                                // are defined in order of priority, and multiple globs may just be alternative ways to
                                // detect the exact same project.
                                // That is, at the example of a PIP project, if a directory contains all three files
                                // "requirements-py2.txt", "requirements-py3.txt" and "setup.py", only consider the
                                // former two as they match the glob with the highest priority, but ignore "setup.py".
                                result.getOrPut(manager) { mutableListOf() } += matchesPerGlob.first()
                            }
                        }

                        return FileVisitResult.CONTINUE
                    }
                }
            )

            return result
        }

        /**
         * Enrich a [package's VCS information][vcsFromPackage] with information deduced from the package's VCS URL or a
         * [list of fallback URLs][fallbackUrls] (the first element that is recognized as a VCS URL is used).
         */
        fun processPackageVcs(vcsFromPackage: VcsInfo, vararg fallbackUrls: String): VcsInfo {
            val normalizedVcsFromPackage = vcsFromPackage.normalize()

            val fallbackVcs = fallbackUrls.mapTo(mutableListOf(VcsHost.parseUrl(normalizedVcsFromPackage.url))) {
                VcsHost.parseUrl(normalizeVcsUrl(it))
            }.find {
                // Ignore fallback VCS information that changes a known type, or where the VCS type is unknown.
                if (normalizedVcsFromPackage.type != VcsType.UNKNOWN) {
                    it.type == normalizedVcsFromPackage.type
                } else {
                    it.type != VcsType.UNKNOWN
                }
            }

            if (fallbackVcs != null) {
                // Enrich (not overwrite) the normalized VCS information from the package...
                val mergedVcs = normalizedVcsFromPackage.merge(fallbackVcs)
                if (mergedVcs != normalizedVcsFromPackage) {
                    // ... but if indeed metadata was enriched, overwrite the URL with the one from the fallback VCS
                    // information to ensure we get the correct base URL if additional VCS information (like a revision
                    // or path) has been split from the original URL.
                    return mergedVcs.copy(url = fallbackVcs.url)
                }
            }

            return normalizedVcsFromPackage
        }

        /**
         * Enrich VCS information determined from the [project's directory][projectDir] with VCS information determined
         * from the [project's metadata][vcsFromProject], if any, and from a [list of fallback URLs][fallbackUrls] (the
         * first element that is recognized as a VCS URL is used).
         */
        fun processProjectVcs(
            projectDir: File,
            vcsFromProject: VcsInfo = VcsInfo.EMPTY,
            vararg fallbackUrls: String
        ): VcsInfo {
            val vcsFromWorkingTree = VersionControlSystem.getPathInfo(projectDir).normalize()
            return vcsFromWorkingTree.merge(processPackageVcs(vcsFromProject, *fallbackUrls))
        }
    }

    /**
     * Optional mapping of found [definitionFiles] before dependency resolution.
     */
    open fun mapDefinitionFiles(definitionFiles: List<File>): List<File> = definitionFiles

    /**
     * Optional step to run before dependency resolution, like checking for prerequisites.
     */
    protected open fun beforeResolution(definitionFiles: List<File>) {}

    /**
     * Optional step to run after dependency resolution, like cleaning up temporary files.
     */
    protected open fun afterResolution(definitionFiles: List<File>) {}

    /**
     * Generate the final result to be returned by this package manager. This function is called at the very end of the
     * execution of this package manager (after [afterResolution]) with the [projectResults] created for the single
     * definition files that have been processed. It can be overridden by sub classes to add additional data to the
     * result. This base implementation produces a result that contains only the passed in map with project results.
     */
    protected open fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>):
            PackageManagerResult = PackageManagerResult(projectResults)

    /**
     * Return a tree of resolved dependencies (not necessarily declared dependencies, in case conflicts were resolved)
     * for all [definitionFiles] which were found by searching the [analysisRoot] directory. By convention, the
     * [definitionFiles] must be absolute. The given [labels] are parameters to the overall analysis of the project and
     * to further stages. They are not interpreted by ORT, but can be used to configure behavior of custom package
     * manager implementations.
     */
    open fun resolveDependencies(definitionFiles: List<File>, labels: Map<String, String>): PackageManagerResult {
        definitionFiles.forEach { definitionFile ->
            requireNotNull(definitionFile.relativeToOrNull(analysisRoot)) {
                "'$definitionFile' must be an absolute path below '$analysisRoot'."
            }
        }

        val result = mutableMapOf<File, List<ProjectAnalyzerResult>>()

        beforeResolution(definitionFiles)

        definitionFiles.forEach { definitionFile ->
            val relativePath = definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath.ifEmpty { "." }

            log.info { "Resolving $managerName dependencies for path '$relativePath'..." }

            val duration = measureTime {
                runCatching {
                    result[definitionFile] = resolveDependencies(definitionFile, labels)
                }.onFailure {
                    it.showStackTrace()

                    // In case of Maven we might be able to do better than inferring the name from the path.
                    val id = if (it is ProjectBuildingException && it.projectId?.isEmpty() == false) {
                        Identifier("Maven:${it.projectId}")
                    } else {
                        Identifier.EMPTY.copy(type = managerName, name = relativePath)
                    }

                    val projectWithIssues = Project.EMPTY.copy(
                        id = id,
                        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                        vcsProcessed = processProjectVcs(definitionFile.parentFile),
                        scopeDependencies = null,
                        scopeNames = sortedSetOf()
                    )

                    val issues = listOf(
                        createAndLogIssue(
                            source = managerName,
                            message = "Resolving $managerName dependencies for path '$relativePath' failed with: " +
                                    it.collectMessagesAsString()
                        )
                    )

                    result[definitionFile] = listOf(ProjectAnalyzerResult(projectWithIssues, sortedSetOf(), issues))
                }
            }

            log.info { "Resolving $managerName dependencies for path '$relativePath' took $duration." }
        }

        afterResolution(definitionFiles)

        return createPackageManagerResult(result).addDependencyGraphIfMissing()
    }

    /**
     * Resolve dependencies for a single absolute [definitionFile] and return a list of [ProjectAnalyzerResult]s, with
     * one result for each project found in the definition file. The given [labels] are parameters to the overall
     * analysis of the project and to further stages. They are not interpreted by ORT, but can be used to configure
     * behavior of custom package manager implementations.
     */
    abstract fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult>

    protected fun requireLockfile(workingDir: File, condition: () -> Boolean) {
        require(analyzerConfig.allowDynamicVersions || condition()) {
            val relativePathString = workingDir.relativeTo(analysisRoot).invariantSeparatorsPath
                .takeUnless { it.isEmpty() } ?: "."

            "No lockfile found in '$relativePathString'. This potentially results in unstable versions of " +
                    "dependencies. To support this, enable the 'allowDynamicVersions' option in '$ORT_CONFIG_FILENAME'."
        }
    }
}

/**
 * Parse a string with metadata about an [author] to extract the author name. Many package managers support
 * such author information in string form that contain additional properties like an email address or a
 * homepage. These additional properties are typically separated from the author name by specific [delimiters],
 * e.g. the email address is often surrounded by angle brackets. This function assumes that the author name is the
 * first portion in the given [author] string before one of the given [delimiters] is found.
 */
fun parseAuthorString(author: String?, vararg delimiters: Char = charArrayOf('<')): String? =
    author?.split(*delimiters, limit = 2)?.firstOrNull()?.trim()?.ifEmpty { null }

private fun PackageManagerResult.addDependencyGraphIfMissing(): PackageManagerResult {
    // If the condition is true, then [CompatibilityDependencyNavigator] constructs a [DependencyGraphNavigator].
    // That construction throws an exception if there is no dependency graph available.
    val isGraphRequired = projectResults.values.flatten().any { it.project.scopeNames != null }

    return if (isGraphRequired && dependencyGraph == null) {
        copy(dependencyGraph = DependencyGraph())
    } else {
        this
    }
}
