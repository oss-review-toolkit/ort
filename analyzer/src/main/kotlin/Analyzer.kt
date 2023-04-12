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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.PackageManager.Companion.excludes
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
import org.ossreviewtoolkit.model.utils.PackageCurationProvider
import org.ossreviewtoolkit.model.utils.addPackageCurations
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.ort.Environment

/**
 * The class to run the analysis. The signatures of public functions in this class define the library API.
 */
class Analyzer(private val config: AnalyzerConfiguration, private val labels: Map<String, String> = emptyMap()) {
    internal companion object : Logging

    data class ManagedFileInfo(
        val absoluteProjectPath: File,
        val managedFiles: Map<PackageManager, List<File>>,
        val repositoryConfiguration: RepositoryConfiguration
    )

    /**
     * Find files recognized by any of [packageManagers] inside [absoluteProjectPath]. The [repositoryConfiguration] is
     * taken into account, e.g. for path excludes and packaga manager options. Instantiate only those package managers
     * that have matching files and return the latter as part of [ManagedFileInfo].
     */
    @JvmOverloads
    fun findManagedFiles(
        absoluteProjectPath: File,
        packageManagers: Collection<PackageManagerFactory> = PackageManager.ENABLED_BY_DEFAULT,
        repositoryConfiguration: RepositoryConfiguration = RepositoryConfiguration()
    ): ManagedFileInfo {
        require(absoluteProjectPath.isAbsolute)

        logger.debug {
            "Using the following configuration settings:\n${repositoryConfiguration.toYaml()}"
        }

        val distinctPackageManagers = packageManagers.distinct()

        // Associate files by the package manager factory that manages them.
        val factoryFiles = if (distinctPackageManagers.size == 1 && absoluteProjectPath.isFile) {
            // If only one package manager is activated and the project path is in fact a file, assume that the file is
            // a definition file for that package manager. This is useful to limit analysis to a single project e.g. for
            // debugging purposes.
            mutableMapOf(distinctPackageManagers.first() to listOf(absoluteProjectPath))
        } else {
            PackageManager.findManagedFiles(
                absoluteProjectPath,
                distinctPackageManagers,
                config.excludes(repositoryConfiguration)
            ).toMutableMap()
        }

        // Associate mapped files by the package manager that manages them.
        val managedFiles = factoryFiles.mapNotNull { (factory, files) ->
            val manager = factory.create(absoluteProjectPath, config, repositoryConfiguration)
            val mappedFiles = manager.mapDefinitionFiles(files)
            Pair(manager, mappedFiles).takeIf { mappedFiles.isNotEmpty() }
        }.toMap(mutableMapOf())

        // Check whether there are unmanaged files (because of deactivated, unsupported, or non-present package
        // managers) which we need to attach to an artificial "unmanaged" project.
        val managedDirs = managedFiles.values.flatten().mapNotNull { it.parentFile }
        val hasOnlyManagedDirs = absoluteProjectPath in managedDirs || absoluteProjectPath.listFiles().orEmpty().all {
            it in managedDirs || it.name in VCS_DIRECTORIES
        }

        if (!hasOnlyManagedDirs) {
            val unmanagedPackageManagerFactory = PackageManager.ALL["Unmanaged"]
            distinctPackageManagers.find { it == unmanagedPackageManagerFactory }
                ?.create(absoluteProjectPath, config, repositoryConfiguration)
                ?.run { managedFiles[this] = listOf(absoluteProjectPath) }
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
        val analyzerResult = analyzeInParallel(info.managedFiles)

        val workingTree = VersionControlSystem.forDirectory(info.absoluteProjectPath)
        val vcs = workingTree?.getInfo().orEmpty()
        val nestedVcs = workingTree?.getNested()?.filter { (path, _) ->
            // Only include nested VCS if they are part of the analyzed directory.
            workingTree.getRootPath().resolve(path).startsWith(info.absoluteProjectPath)
        }.orEmpty()
        val repository = Repository(vcs = vcs, nestedRepositories = nestedVcs, config = info.repositoryConfiguration)

        val endTime = Instant.now()

        val toolVersions = mutableMapOf<String, String>()

        info.managedFiles.keys.forEach { manager ->
            if (manager is CommandLineTool) {
                toolVersions[manager.managerName] = manager.getVersion()
            }
        }

        val run = AnalyzerRun(startTime, endTime, Environment(toolVersions = toolVersions), config, analyzerResult)

        return OrtResult(repository = repository, analyzer = run).addPackageCurations(packageCurationProviders)
    }

    private fun analyzeInParallel(managedFiles: Map<PackageManager, List<File>>): AnalyzerResult {
        val state = AnalyzerState()

        val packageManagerDependencies = determinePackageManagerDependencies(managedFiles)

        runBlocking {
            managedFiles.entries.map { (manager, files) ->
                PackageManagerRunner(
                    manager = manager,
                    definitionFiles = files,
                    labels = labels,
                    mustRunAfter = packageManagerDependencies[manager].orEmpty(),
                    finishedPackageManagersState = state.finishedPackageManagersState,
                    onResult = { result -> state.addResult(manager, result) }
                )
            }.forEach { launch { it.start() } }

            state.finishedPackageManagersState.first { finishedPackageManagers ->
                finishedPackageManagers.containsAll(managedFiles.keys.map { it.managerName })
            }
        }

        val excludes = managedFiles.keys.firstOrNull()?.excludes ?: Excludes.EMPTY
        return state.buildResult(excludes)
    }

    private fun determinePackageManagerDependencies(
        managedFiles: Map<PackageManager, List<File>>
    ): Map<PackageManager, Set<String>> {
        val packageManagersWithFiles = managedFiles.keys.associateByTo(sortedMapOf(String.CASE_INSENSITIVE_ORDER)) {
            it.managerName
        }

        val result = mutableMapOf<PackageManager, MutableSet<String>>()

        managedFiles.keys.forEach { packageManager ->
            val dependencies = packageManager.findPackageManagerDependencies(managedFiles)
            val mustRunAfterConfig = config.getPackageManagerConfiguration(packageManager.managerName)?.mustRunAfter

            // Configured mustRunAfter dependencies override programmatic mustRunAfter dependencies.
            val mustRunAfter = mustRunAfterConfig?.toSet() ?: dependencies.mustRunAfter

            mustRunAfter.forEach { name ->
                val managerForName = packageManagersWithFiles[name]

                if (managerForName == null) {
                    logger.debug {
                        "Ignoring that ${packageManager.managerName} must run after $name, because there are no " +
                                "definition files for $name."
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
                        "Ignoring that ${packageManager.managerName} must run before $name, because there are no " +
                                "definition files for $name."
                    }
                } else {
                    result.getOrPut(managerForName) { mutableSetOf() } += packageManager.managerName
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
                    builder.addDependencyGraph(manager.managerName, it).addPackages(result.sharedPackages)
                }
                _finishedPackageManagersState.value = (_finishedPackageManagersState.value + manager.managerName)
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
                    Analyzer.logger.info {
                        "${manager.managerName} is waiting for the following package managers to complete: " +
                                remaining.joinToString(postfix = ".")
                    }
                }

                remaining.isEmpty()
            }
        }

        run()
    }

    private suspend fun run() {
        Analyzer.logger.info { "Starting ${manager.managerName} analysis." }

        withContext(Dispatchers.IO) {
            val result = manager.resolveDependencies(definitionFiles, labels)

            Analyzer.logger.info { "Finished ${manager.managerName} analysis." }

            onResult(result)
        }
    }
}
