/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.ProjectResults
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.PYPROJECT_FILENAME
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.PythonIndexClient
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.ort.runBlocking

private const val PROJECT_TYPE = "Pylock"

/** The lockfile name as specified by PEP 751, and the pattern for named lockfiles like `pylock.linux.toml`. */
private const val LOCKFILE_NAME = "pylock.toml"
private const val NAMED_LOCKFILE_GLOB = "pylock.*.toml"

private const val PIP_INDEX_URL_ENV = "PIP_INDEX_URL"

private const val MAX_CONCURRENT_INDEX_REQUESTS = 20

data class PylockConfig(
    /**
     * The URL of the simple index to retrieve package metadata like licenses from instead of PyPI, e.g. to use a
     * proxy or mirror. Packages the lockfile records another index for are still looked up on that index. If not
     * set, the value of the `PIP_INDEX_URL` environment variable is used the same way.
     */
    val indexUrl: String?
)

/**
 * A package manager for Python that reads a committed lockfile as specified by
 * [PEP 751](https://peps.python.org/pep-0751/), which pip, uv, PDM and Poetry can write. Nothing is resolved or
 * installed. Package metadata like licenses is retrieved from the package index, and only the dependency
 * relationships declared in the lockfile are published.
 *
 * Environment markers are not evaluated, so the result contains the packages for all environments of the lockfile.
 *
 * The packages created from a lockfile differ in metadata from those the PIP package manager creates for the same
 * dependencies via Python Inspector, while ORT requires packages with the same identifier to be equal. So the same
 * directory must not be analyzed by both package managers, e.g. by disabling PIP if a `requirements.txt` is kept
 * next to the lockfile.
 */
@OrtPlugin(
    displayName = "Pylock",
    summary = "The parser for PEP 751 lockfiles (pylock.toml) of Python projects.",
    factory = PackageManagerFactory::class
)
class Pylock internal constructor(
    override val descriptor: PluginDescriptor = PylockFactory.descriptor,
    private val config: PylockConfig,
    indexClientFactory: (String?) -> PythonIndexClient
) : PackageManager(PROJECT_TYPE) {
    constructor(descriptor: PluginDescriptor = PylockFactory.descriptor, config: PylockConfig) :
        this(descriptor, config, ::PythonIndexClient)

    // A "pyproject.toml" does not tell whether a PEP 751 lockfile exists, so the lockfile is the definition file. Both
    // globs share one entry, as only the first matching glob is considered per directory.
    override val globsForDefinitionFiles = listOf("{$LOCKFILE_NAME,$NAMED_LOCKFILE_GLOB}")

    private val indexClient by lazy {
        val indexUrl = config.indexUrl?.takeUnless { it.isBlank() }
            ?: Os.env[PIP_INDEX_URL_ENV]?.takeUnless { it.isBlank() }

        indexClientFactory(indexUrl)
    }

    private val dependencyHandler = PylockDependencyHandler()
    private val graphBuilder = DependencyGraphBuilder(dependencyHandler)

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val pylock = parsePylockFile(definitionFile)
        val pyproject = parsePyprojectFile(workingDir.resolve(PYPROJECT_FILENAME))

        logger.info {
            val environments = pylock.environments.takeIf { it.isNotEmpty() }?.let { " for the environments $it" }
            "The lockfile '$definitionFile' was created by '${pylock.createdBy}'${environments.orEmpty()}."
        }

        // Directory entries are the project itself or workspace members, so there is nothing to record about them.
        val (directoryEntries, packageEntries) = pylock.packages.partition { it.directory != null }

        directoryEntries.forEach {
            logger.info {
                "Skipping the entry for '${it.name}' as it refers to the local directory '${it.directory?.path}'."
            }
        }

        val metadataByEntry = runBlocking(Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_INDEX_REQUESTS)) {
            packageEntries.map { entry -> async { entry to indexClient.getMetadata(entry) } }.awaitAll()
        }.toMap()

        val issues = buildList {
            val entriesWithoutMetadata = metadataByEntry.filterValues { it == null }.keys.filter { it.isIndexPackage }
            if (entriesWithoutMetadata.isNotEmpty()) {
                add(
                    createAndLogIssue(
                        "No metadata could be retrieved from the package index for ${entriesWithoutMetadata.size} " +
                            "package(s), so their licenses are unknown: " +
                            entriesWithoutMetadata.joinToString { it.toIdentifier().toCoordinates() },
                        Severity.WARNING
                    )
                )
            }
        }

        val dependencies = pylock.packages.resolveDependencies()
        dependencyHandler.addEntries(definitionFile, dependencies, metadataByEntry)

        val project = createProject(pyproject, analysisRoot, definitionFile)

        pylock.packages.getDirectDependenciesByScope(dependencies).forEach { (scopeName, entries) ->
            graphBuilder.addDependencies(project.id, scopeName, entries.map { PylockDependency(definitionFile, it) })
        }

        return listOf(
            ProjectAnalyzerResult(
                project = project.copy(scopeNames = graphBuilder.scopesFor(project.id)),
                packages = emptySet(),
                issues = issues
            )
        )
    }

    override fun createPackageManagerResult(projectResults: ProjectResults): PackageManagerResult =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    private fun createProject(pyproject: PyprojectFile?, analysisRoot: File, definitionFile: File): Project {
        val workingDir = definitionFile.parentFile
        val projectMetadata = pyproject?.getProjectMetadata()

        // Named lockfiles of the same project, e.g. one per platform, must not get the same identifier.
        val lockfileName = definitionFile.name.takeUnless { it == LOCKFILE_NAME }
            ?.removePrefix("pylock.")
            ?.removeSuffix(".toml")
        val projectName = projectMetadata?.name?.let { name ->
            listOfNotNull(name, lockfileName).joinToString("-")
        } ?: getFallbackProjectName(analysisRoot, definitionFile)

        val projectVersion = projectMetadata?.version ?: VersionControlSystem.getCloneInfo(workingDir).revision

        return Project(
            id = Identifier(type = projectType, namespace = "", name = projectName, version = projectVersion),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = projectMetadata?.getAuthors().orEmpty(),
            declaredLicenses = projectMetadata?.getDeclaredLicenses().orEmpty(),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = processProjectVcs(
                workingDir,
                VcsInfo.EMPTY,
                *projectMetadata?.urls?.values.orEmpty().toTypedArray()
            ),
            description = projectMetadata?.description.orEmpty(),
            homepageUrl = projectMetadata?.getHomepageUrl().orEmpty(),
            scopeNames = emptySet()
        )
    }
}
