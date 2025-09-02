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
import java.time.Instant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager.Companion.excludes
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.includes
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.config.setPackageCurations
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.runBlocking

/**
 * The class to run the analysis. The signatures of public functions in this class define the library API.
 */
class Analyzer(private val config: AnalyzerConfiguration, private val labels: Map<String, String> = emptyMap()) {
    data class ManagedFileInfo(
        val absoluteProjectPath: File,
        val managedFiles: Map<PackageManager, List<File>>,
        val repositoryConfiguration: RepositoryConfiguration
    )

    /**
     * Find files recognized by any of [packageManagers] inside [absoluteProjectPath]. The [repositoryConfiguration] is
     * taken into account, e.g. for path excludes and package manager options. Instantiate only those package managers
     * that have matching files and return the latter as part of [ManagedFileInfo].
     */
    @JvmOverloads
    fun findManagedFiles(
        absoluteProjectPath: File,
        packageManagers: Collection<PackageManagerFactory>,
        repositoryConfiguration: RepositoryConfiguration = RepositoryConfiguration()
    ): ManagedFileInfo {
        require(absoluteProjectPath.isAbsolute)

        logger.debug {
            "Using the following configuration settings:\n${repositoryConfiguration.toYaml()}"
        }

        val distinctPackageManagers = packageManagers.distinct().map {
            val pluginOptions = config.getPackageManagerConfiguration(it.descriptor.id)?.options.orEmpty()
            it.create(PluginConfig(pluginOptions))
        }

        // Associate files by the package manager factory that manages them.
        val managedFiles = if (distinctPackageManagers.size == 1 && absoluteProjectPath.isFile) {
            // If only one package manager is activated and the project path is in fact a file, assume that the file is
            // a definition file for that package manager. This is useful to limit analysis to a single project e.g. for
            // debugging purposes.
            mapOf(distinctPackageManagers.first() to listOf(absoluteProjectPath))
        } else {
            PackageManager.findManagedFiles(
                absoluteProjectPath,
                distinctPackageManagers,
                config.excludes(repositoryConfiguration),
                config.includes(repositoryConfiguration)
            )
        }.mapNotNull { (manager, files) ->
            val mappedFiles = manager.mapDefinitionFiles(absoluteProjectPath, files, config)
            Pair(manager, mappedFiles).takeIf { mappedFiles.isNotEmpty() }
        }.toMap(mutableMapOf())

        // Fail early if multiple managers for the same project type are enabled.
        managedFiles.keys.groupBy { it.projectType }.forEach { (projectType, managers) ->
            val managerNames = managers.map { it.descriptor.displayName }
            requireNotNull(managers.singleOrNull()) {
                "All of the $managerNames managers are able to manage '$projectType' projects. Please enable only " +
                    "one of them."
            }
        }

        // Check whether there are unmanaged files (because of deactivated, unsupported, or non-present package
        // managers) which need to get attached to an artificial "unmanaged" project.
        val managedDirs = managedFiles.values.flatten().mapNotNull { it.parentFile }
        val hasOnlyManagedDirs = absoluteProjectPath in managedDirs || absoluteProjectPath.walk().maxDepth(1)
            .all { it.isDirectory && (it in managedDirs || it.name in VCS_DIRECTORIES) }

        if (!hasOnlyManagedDirs) {
            distinctPackageManagers.find { it.descriptor.id == "Unmanaged" }
                ?.also { managedFiles[it] = listOf(absoluteProjectPath) }
        }

        return ManagedFileInfo(absoluteProjectPath, managedFiles, repositoryConfiguration)
    }

    /**
     * Return the result of analyzing the given [managed file][info]. The given [packageCurationProviders] must be
     * ordered highest-priority-first.
     */
    @JvmOverloads
    fun analyze(
        info: ManagedFileInfo,
        packageCurationProviders: List<Pair<String, PackageCurationProvider>> = emptyList()
    ): OrtResult {
        val startTime = Instant.now()

        // Resolve dependencies per package manager.
        val analyzerResult = analyzeInParallel(info)

        val workingTree = VersionControlSystem.forDirectory(info.absoluteProjectPath)
        val vcs = workingTree?.getInfo().orEmpty()
        val nestedVcs = workingTree?.getNested()?.filter { (path, _) ->
            // Only include nested VCS if they are part of the analyzed directory.
            workingTree.getRootPath().resolve(path).startsWith(info.absoluteProjectPath)
        }.orEmpty()
        val repository = Repository(vcs = vcs, nestedRepositories = nestedVcs, config = info.repositoryConfiguration)

        val endTime = Instant.now()

        val run = AnalyzerRun(startTime, endTime, Environment(), config, analyzerResult)

        return OrtResult(repository = repository, analyzer = run).setPackageCurations(packageCurationProviders)
    }

    private fun analyzeInParallel(info: ManagedFileInfo): AnalyzerResult {
        logger.info { "Calling before resolution hooks for ${info.managedFiles.size} manager(s)." }

        info.managedFiles.forEach { (manager, definitionFiles) ->
            manager.beforeResolution(info.absoluteProjectPath, definitionFiles, config)
        }

        val state = AnalyzerState()
        val packageManagerDependencies = determinePackageManagerDependencies(info)
        val excludes = config.excludes(info.repositoryConfiguration)

        runBlocking {
            // The excludes are passed to allow the package managers to take in account the scope excludes. Since there
            // is no scope includes, the includes are not passed to the package managers.
            info.managedFiles.entries.map { (manager, files) ->
                PackageManagerRunner(
                    manager = manager,
                    definitionFiles = files,
                    analysisRoot = info.absoluteProjectPath,
                    excludes = excludes,
                    analyzerConfig = config,
                    labels = labels,
                    mustRunAfter = packageManagerDependencies[manager].orEmpty(),
                    finishedPackageManagersState = state.finishedPackageManagersState,
                    onResult = { result -> state.addResult(manager, result) }
                )
            }.forEach { launch { it.start() } }

            state.finishedPackageManagersState.first { finishedPackageManagers ->
                finishedPackageManagers.containsAll(info.managedFiles.keys.map { it.descriptor.id })
            }
        }

        logger.info { "Calling after resolution hooks for ${info.managedFiles.size} manager(s)." }

        info.managedFiles.forEach { (manager, definitionFiles) ->
            manager.afterResolution(info.absoluteProjectPath, definitionFiles)
        }

        return state.buildResult(excludes)
    }

    private fun determinePackageManagerDependencies(info: ManagedFileInfo): Map<PackageManager, Set<String>> {
        val packageManagersWithFiles =
            info.managedFiles.keys.associateByTo(sortedMapOf(String.CASE_INSENSITIVE_ORDER)) {
                it.descriptor.id
            }

        val result = mutableMapOf<PackageManager, MutableSet<String>>()

        info.managedFiles.keys.forEach { packageManager ->
            val dependencies =
                packageManager.findPackageManagerDependencies(info.absoluteProjectPath, info.managedFiles, config)
            val mustRunAfterConfig = config.getPackageManagerConfiguration(packageManager.descriptor.id)?.mustRunAfter

            // Configured mustRunAfter dependencies override programmatic mustRunAfter dependencies.
            val mustRunAfter = mustRunAfterConfig?.toSet() ?: dependencies.mustRunAfter

            mustRunAfter.forEach { name ->
                val managerForName = packageManagersWithFiles[name]

                if (managerForName == null) {
                    logger.debug {
                        "Ignoring that ${packageManager.descriptor.displayName} must run after $name, because there " +
                            "are no definition files for $name."
                    }
                } else {
                    result.getOrPut(packageManager) { mutableSetOf() } += name
                }
            }

            // Configured mustRunAfter dependencies override programmatic mustRunBefore dependencies.
            val mustRunBefore = dependencies.mustRunBefore.takeUnless { mustRunAfterConfig != null }

            mustRunBefore?.forEach { name ->
                val managerForName = packageManagersWithFiles[name]

                if (managerForName == null) {
                    logger.debug {
                        "Ignoring that ${packageManager.descriptor.displayName} must run before $name, because there " +
                            "are no definition files for $name."
                    }
                } else {
                    result.getOrPut(managerForName) { mutableSetOf() } += packageManager.descriptor.id
                }
            }
        }

        return result
    }
}

private class AnalyzerState {
    private val builder = AnalyzerResultBuilder()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val addMutex = Mutex()

    private val _finishedPackageManagersState = MutableStateFlow<Set<String>>(emptySet())
    val finishedPackageManagersState = _finishedPackageManagersState.asStateFlow()

    fun addResult(manager: PackageManager, result: PackageManagerResult) {
        scope.launch {
            addMutex.withLock {
                result.projectResults.values.flatten().forEach { builder.addResult(it) }
                result.dependencyGraph?.let {
                    builder.addDependencyGraph(manager.descriptor.id, it).addPackages(result.sharedPackages)
                }

                _finishedPackageManagersState.value = (_finishedPackageManagersState.value + manager.descriptor.id)
                    .toSortedSet(String.CASE_INSENSITIVE_ORDER)
            }
        }
    }

    fun buildResult(excludes: Excludes) = builder.build(excludes)
}

/**
 * A class to manage running a [PackageManager].
 */
private class PackageManagerRunner(
    /**
     * The [PackageManager] to run.
     */
    val manager: PackageManager,

    /**
     * The list of definition files to analyze.
     */
    val definitionFiles: List<File>,

    /**
     * The root directory of the analysis.
     */
    val analysisRoot: File,

    /**
     * The [Excludes] to apply.
     */
    val excludes: Excludes,

    /**
     * The [AnalyzerConfiguration] to use.
     */
    val analyzerConfig: AnalyzerConfiguration,

    /**
     * The labels passed to ORT.
     */
    val labels: Map<String, String>,

    /**
     * A set of the names of package managers that this package manager must run after.
     */
    val mustRunAfter: Set<String>,

    /**
     * A [StateFlow] that updates with the set of already finished package managers.
     */
    val finishedPackageManagersState: StateFlow<Set<String>>,

    /**
     * A callback for this package manager to return its result.
     */
    val onResult: (PackageManagerResult) -> Unit
) {
    /**
     * Start the runner. If [mustRunAfter] is not empty, it waits until [finishedPackageManagersState] contains all
     * required package managers.
     */
    suspend fun start() {
        if (mustRunAfter.isNotEmpty()) {
            finishedPackageManagersState.first { finishedPackageManagers ->
                val remaining = mustRunAfter - finishedPackageManagers

                if (remaining.isNotEmpty()) {
                    logger.info {
                        "${manager.descriptor.displayName} is waiting for the following package managers to " +
                            "complete: ${remaining.joinToString(postfix = ".")}"
                    }
                }

                remaining.isEmpty()
            }
        }

        run()
    }

    private suspend fun run() {
        logger.info { "Starting ${manager.descriptor.displayName} analysis." }

        withContext(Dispatchers.IO.limitedParallelism(20)) {
            val result = manager.resolveDependencies(analysisRoot, definitionFiles, excludes, analyzerConfig, labels)

            logger.info { "Finished ${manager.descriptor.displayName} analysis." }

            onResult(result)
        }
    }
}
