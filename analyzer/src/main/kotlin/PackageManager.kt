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

package org.ossreviewtoolkit.analyzer

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path

import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.measureTime

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.plugins.api.Plugin
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.common.collapseWhitespace
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.isSymbolicLink
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.ort.showStackTrace

typealias ManagedProjectFiles = Map<PackageManager, List<File>>
typealias ProjectResults = Map<File, List<ProjectAnalyzerResult>>

/**
 * A class to represent a package manager that handles projects of the given [projectType].
 */
abstract class PackageManager(val projectType: String) : Plugin {
    companion object {
        private val PACKAGE_MANAGER_DIRECTORIES = setOf(
            // Ignore intermediate build system directories.
            ".gradle",
            ".yarn",
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
         * depth-first so that root project files are found before any subproject files for a specific manager. Path
         * excludes defined by the given [excludes] are taken into account; the corresponding directories are skipped.
         */
        fun findManagedFiles(
            directory: File,
            packageManagers: Collection<PackageManager>,
            excludes: Excludes = Excludes.EMPTY,
            includes: Includes = Includes.EMPTY
        ): ManagedProjectFiles {
            require(directory.isDirectory) {
                "The provided path is not a directory: ${directory.absolutePath}"
            }

            logger.debug { "Searching for managed files using the following excludes: $excludes" }

            val result = mutableMapOf<PackageManager, MutableList<File>>()
            val rootPath = directory.toPath()
            val distinctPackageManagers = packageManagers.distinct()

            // Note: Even if the directory is not included and other includes are defined, it has to be walked as
            // subdirectories or files may be included.
            directory.walk().onEnter { dir ->
                val dirAsPath = dir.toPath()

                when {
                    IGNORED_DIRECTORY_MATCHERS.any { it.matches(dirAsPath) } -> {
                        logger.info { "Not analyzing directory '$dir' as it is hard-coded to be ignored." }
                        false
                    }

                    excludes.isPathExcluded(rootPath, dirAsPath) -> {
                        logger.info { "Not analyzing directory '$dir' as it is excluded." }
                        false
                    }

                    dir.isSymbolicLink -> {
                        logger.info { "Not following symbolic link to directory '$dir'." }
                        false
                    }

                    else -> true
                }
            }.filter { it.isDirectory }.forEach { dir ->
                val filesInCurrentDir = dir.walk().maxDepth(1).filterTo(mutableListOf()) {
                    val isIncluded = includes.isPathIncluded(rootPath, it.toPath())
                    it.isFile && !excludes.isPathExcluded(rootPath, it.toPath()) && isIncluded
                }

                distinctPackageManagers.forEach { manager ->
                    // Create a list of lists of matching files per glob.
                    val matchesPerGlob = manager.matchersForDefinitionFiles.mapNotNull { glob ->
                        // Create a list of files in the current directory that match the current glob.
                        val filesMatchingGlob = filesInCurrentDir.filter { glob.matches(it.toPath()) }
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
            }

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
                    // information to ensure to get the correct base URL if additional VCS information (like a revision
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

        /**
         * Return an [Excludes] instance to be applied during analysis based on the given [repositoryConfiguration].
         * If this [AnalyzerConfiguration] has the [AnalyzerConfiguration.skipExcluded] flag set to true, the
         * excludes configured in [repositoryConfiguration] are actually applied. Otherwise, return an empty [Excludes]
         * object. This means that all dependencies are collected, and excludes are applied later on the report level.
         */
        internal fun AnalyzerConfiguration.excludes(repositoryConfiguration: RepositoryConfiguration): Excludes =
            repositoryConfiguration.excludes.takeIf { skipExcluded } ?: Excludes.EMPTY

        /**
         * Return an [Includes] instance to be applied during analysis based on the given [repositoryConfiguration].
         * If this [AnalyzerConfiguration] has the [AnalyzerConfiguration.skipExcluded] flag set to true, the
         * includes configured in [repositoryConfiguration] are actually applied. Otherwise, return an empty [Includes]
         * object. This means that all dependencies are collected, and includes are applied later on the report level.
         */
        internal fun AnalyzerConfiguration.includes(repositoryConfiguration: RepositoryConfiguration): Includes =
            repositoryConfiguration.includes.takeIf { skipExcluded } ?: Includes.EMPTY

        /**
         * Check whether the given [path] interpreted relatively against [root] is matched by a path exclude in this
         * [Excludes] object.
         */
        private fun Excludes.isPathExcluded(root: Path, path: Path): Boolean =
            isPathExcluded(root.relativize(path).invariantSeparatorsPathString)

        /**
         * Check whether the given [path] interpreted relatively against [root] is matched by a path include in this
         * [Includes] object.
         */
        private fun Includes.isPathIncluded(root: Path, path: Path): Boolean =
            isPathIncluded(root.relativize(path).invariantSeparatorsPathString)

        /**
         * Get a fallback project name from the [definitionFile] path relative to the [analysisRoot]. This function
         * should be used if the project name cannot be determined from the project's metadata.
         */
        fun getFallbackProjectName(analysisRoot: File, definitionFile: File) =
            definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath
    }

    /**
     * The prioritized list of glob patterns of definition files supported by this package manager. Only all matches of
     * the first glob having any matches are considered.
     */
    abstract val globsForDefinitionFiles: List<String>

    val matchersForDefinitionFiles by lazy {
        globsForDefinitionFiles.map {
            FileSystems.getDefault().getPathMatcher("glob:**/$it")
        }
    }

    /**
     * Optional mapping of found [definitionFiles] before dependency resolution.
     */
    open fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> = definitionFiles

    /**
     * Return if this package manager must run before or after certain other package managers. This can manually be
     * configured by the user in [PackageManagerConfiguration.mustRunAfter], but in some cases it is possible to
     * determine such dependencies automatically.
     */
    open fun findPackageManagerDependencies(
        analysisRoot: File,
        managedFiles: Map<PackageManager, List<File>>,
        analyzerConfig: AnalyzerConfiguration
    ): PackageManagerDependencyResult =
        PackageManagerDependencyResult(mustRunBefore = emptySet(), mustRunAfter = emptySet())

    /**
     * Optional step to run before dependency resolution, like checking for prerequisites. This function is called
     * before [resolveDependencies] is called for any enabled package manager. It does not respect any "mustRunAfter"
     * configuration.
     */
    open fun beforeResolution(analysisRoot: File, definitionFiles: List<File>, analyzerConfig: AnalyzerConfiguration) {}

    /**
     * Optional step to run after dependency resolution, like cleaning up temporary files. This function is called after
     * [resolveDependencies] has finished for all enabled package managers. It does not respect any "mustRunAfter"
     * configuration.
     */
    open fun afterResolution(analysisRoot: File, definitionFiles: List<File>) {}

    /**
     * Generate the final result to be returned by this package manager. This function is called at the very end of the
     * execution of this package manager (after [afterResolution]) with the [projectResults] created for the single
     * definition files that have been processed. It can be overridden by subclasses to add additional data to the
     * result. This base implementation produces a result that contains only the passed in map with project results.
     */
    protected open fun createPackageManagerResult(projectResults: ProjectResults) = PackageManagerResult(projectResults)

    /**
     * Return a tree of resolved dependencies (not necessarily declared dependencies, in case conflicts were resolved)
     * for all [definitionFiles] which were found by searching the [analysisRoot] directory. By convention, the
     * [definitionFiles] must be absolute. The given [labels] are parameters to the overall analysis of the project and
     * to further stages. They are not interpreted by ORT, but can be used to configure behavior of custom package
     * manager implementations.
     */
    open fun resolveDependencies(
        analysisRoot: File,
        definitionFiles: List<File>,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): PackageManagerResult {
        definitionFiles.forEach { definitionFile ->
            requireNotNull(definitionFile.relativeToOrNull(analysisRoot)) {
                "'$definitionFile' must be an absolute path below '$analysisRoot'."
            }
        }

        val result = mutableMapOf<File, List<ProjectAnalyzerResult>>()

        definitionFiles.forEach { definitionFile ->
            val relativePath = definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath.ifEmpty { "." }

            logger.info { "Using ${descriptor.displayName} to resolve dependencies for path '$relativePath'..." }

            val duration = measureTime {
                runCatching {
                    result[definitionFile] =
                        resolveDependencies(analysisRoot, definitionFile, excludes, analyzerConfig, labels)
                }.onFailure {
                    it.showStackTrace()

                    val id = Identifier.EMPTY.copy(type = projectType, name = relativePath)

                    val projectWithIssues = Project.EMPTY.copy(
                        id = id,
                        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                        vcsProcessed = processProjectVcs(definitionFile.parentFile),
                        scopeDependencies = null,
                        scopeNames = emptySet()
                    )

                    val issues = listOf(
                        createAndLogIssue(
                            "${descriptor.displayName} failed to resolve dependencies for path '$relativePath': " +
                                it.collectMessages()
                        )
                    )

                    result[definitionFile] = listOf(ProjectAnalyzerResult(projectWithIssues, emptySet(), issues))
                }
            }

            logger.info { "${descriptor.displayName} resolved dependencies for path '$relativePath' in $duration." }
        }

        return createPackageManagerResult(result).addDependencyGraphIfMissing()
    }

    /**
     * Resolve dependencies for a single absolute [definitionFile] and return a list of [ProjectAnalyzerResult]s, with
     * one result for each project found in the definition file. The [excludes] define which scopes should be excluded
     * from analysis. The given [labels] are parameters to the overall analysis of the project and to further stages.
     * They are not interpreted by ORT, but can be used to configure behavior of custom package manager implementations.
     */
    abstract fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult>

    protected fun requireLockfile(
        analysisRoot: File,
        workingDir: File,
        allowDynamicVersions: Boolean,
        condition: () -> Boolean
    ) {
        require(allowDynamicVersions || condition()) {
            val relativePathString = workingDir.relativeTo(analysisRoot).invariantSeparatorsPath.ifEmpty { "." }

            "No lockfile found in '$relativePathString'. This potentially results in unstable versions of " +
                "dependencies. To support this, enable the 'allowDynamicVersions' option in '$ORT_CONFIG_FILENAME'."
        }
    }

    /**
     * Remove all packages from the contained [ProjectAnalyzerResult]s which are also projects.
     */
    protected fun ProjectResults.filterProjectPackages(): ProjectResults {
        val projectIds = flatMapTo(mutableSetOf()) { (_, projectResult) -> projectResult.map { it.project.id } }

        return mapValues { entry ->
            entry.value.map { projectResult ->
                val projectReferences = projectResult.packages.filterTo(mutableSetOf()) { it.id in projectIds }
                projectResult.takeIf { projectReferences.isEmpty() }
                    ?: projectResult.copy(packages = projectResult.packages - projectReferences)
                        .also {
                            logger.info { "Removing ${projectReferences.size} packages that are projects." }

                            logger.debug { projectReferences.joinToString { it.id.toCoordinates() } }
                        }
            }
        }
    }
}

/**
 * Parse a string with metadata about an [author] that several package managers use and try to extract the author's
 * name, email address, and homepage. These properties are typically surrounded by specific delimiters, e.g. the email
 * address is often surrounded by angle brackets (see [emailDelimiters]) and the homepage is often surrounded by
 * parentheses (see [homepageDelimiters]). Return [AuthorInfo] for these properties where unavailable ones are set to
 * null.
 */
fun parseAuthorString(
    author: String?,
    emailDelimiters: Pair<Char, Char> = '<' to '>',
    homepageDelimiters: Pair<Char, Char> = '(' to ')'
): Set<AuthorInfo> =
    author?.split(',', '\n')?.mapTo(mutableSetOf()) { singleAuthor ->
        var cleanedAuthor = singleAuthor
        var email: String? = null
        var homepage: String? = null

        // Extract an email address and remove it from the original autgor string.
        val e = emailDelimiters.toList().map { Regex.escape(it.toString()) }
        val emailRegex = Regex("${e.first()}(.+@.+)${e.last()}")
        cleanedAuthor = cleanedAuthor.replace(emailRegex) {
            email = it.groupValues.last()
            ""
        }

        // Extract a homepage URL and remove it from the original autgor string.
        val h = homepageDelimiters.toList().map { Regex.escape(it.toString()) }
        val homepageRegex = Regex("${h.first()}(.+(?:://|www|.).+)${h.last()}")
        cleanedAuthor = cleanedAuthor.replace(homepageRegex) {
            homepage = it.groupValues.last()
            ""
        }

        AuthorInfo(cleanedAuthor.collapseWhitespace().ifEmpty { null }, email, homepage)
    }.orEmpty()

/**
 * Information about an author, including the [name], [email] address, and [homepage] URL.
 */
data class AuthorInfo(val name: String?, val email: String?, val homepage: String?)

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
