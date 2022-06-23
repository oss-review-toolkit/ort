/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

import org.ossreviewtoolkit.analyzer.managers.Unmanaged
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.log

/**
 * The class to run the analysis. The signatures of public functions in this class define the library API.
 */
class Analyzer(private val config: AnalyzerConfiguration, private val labels: Map<String, String> = emptyMap()) {
    data class ManagedFileInfo(
        val absoluteProjectPath: File,
        val managedFiles: Map<PackageManager, List<File>>,
        val repositoryConfiguration: RepositoryConfiguration
    )

    @JvmOverloads
    fun findManagedFiles(
        absoluteProjectPath: File,
        packageManagers: Set<PackageManagerFactory> = PackageManager.ALL,
        repositoryConfiguration: RepositoryConfiguration = RepositoryConfiguration()
    ): ManagedFileInfo {
        require(absoluteProjectPath.isAbsolute)

        log.debug {
            "Using the following configuration settings:\n${yamlMapper.writeValueAsString(repositoryConfiguration)}"
        }

        // Associate files by the package manager factory that manages them.
        val factoryFiles = if (packageManagers.size == 1 && absoluteProjectPath.isFile) {
            // If only one package manager is activated and the project path is in fact a file, assume that the file is
            // a definition file for that package manager. This is useful to limit analysis to a single project e.g. for
            // debugging purposes.
            mutableMapOf(packageManagers.first() to listOf(absoluteProjectPath))
        } else {
            PackageManager.findManagedFiles(absoluteProjectPath, packageManagers).toMutableMap()
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
            packageManagers.find { it is Unmanaged.Factory }
                ?.create(absoluteProjectPath, config, repositoryConfiguration)
                ?.run { managedFiles[this] = listOf(absoluteProjectPath) }
        }

        return ManagedFileInfo(absoluteProjectPath, managedFiles, repositoryConfiguration)
    }

    @JvmOverloads
    fun analyze(
        info: ManagedFileInfo,
        curationProvider: PackageCurationProvider = PackageCurationProvider.EMPTY
    ): OrtResult {
        val startTime = Instant.now()

        // Resolve dependencies per package manager.
        val analyzerResult = analyzeInParallel(info.managedFiles, curationProvider)

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

        return OrtResult(repository, run)
    }

    private fun analyzeInParallel(
        managedFiles: Map<PackageManager, List<File>>,
        curationProvider: PackageCurationProvider
    ): AnalyzerResult {
        val state = AnalyzerState(curationProvider)

        val packageManagerDependencies = determinePackageManagerDependencies(managedFiles)

        runBlocking {
            managedFiles.entries.map { (manager, files) ->
                val mustRunAfter = config.getPackageManagerConfiguration(manager.managerName)?.mustRunAfter?.toSet()
                    ?: packageManagerDependencies[manager]?.mapTo(mutableSetOf()) { it.managerName }.orEmpty()

                PackageManagerRunner(
                    manager = manager,
                    definitionFiles = files,
                    labels = labels,
                    mustRunAfter = mustRunAfter,
                    finishedPackageManagersState = state.finishedPackageManagersState,
                    onResult = { result -> state.addResult(manager, result) }
                )
            }.forEach { launch { it.start() } }

            state.finishedPackageManagersState.first { finishedPackageManagers ->
                finishedPackageManagers.containsAll(managedFiles.keys.map { it.managerName })
            }
        }

        return state.buildResult()
    }

    private fun determinePackageManagerDependencies(
        managedFiles: Map<PackageManager, List<File>>
    ): Map<PackageManager, Set<PackageManager>> {
        val allPackageManagers =
            managedFiles.keys.associateBy { it.managerName }.toSortedMap(String.CASE_INSENSITIVE_ORDER)

        val result = mutableMapOf<PackageManager, MutableSet<PackageManager>>()

        managedFiles.keys.forEach { packageManager ->
            val dependencies = packageManager.findPackageManagerDependencies(managedFiles)
            dependencies.mustRunAfter.forEach { name ->
                val managerForName = allPackageManagers[name]

                if (managerForName == null) {
                    log.debug {
                        "Ignoring that ${packageManager.managerName} must run after $name, because there are no " +
                                "definition files for $name."
                    }
                } else {
                    result.getOrPut(packageManager) { mutableSetOf() } += managerForName
                }
            }

            dependencies.mustRunBefore.forEach { name ->
                val managerForName = allPackageManagers[name]

                if (managerForName == null) {
                    log.debug {
                        "Ignoring that ${packageManager.managerName} must run before $name, because there are no " +
                                "definition files for $name."
                    }
                } else {
                    result.getOrPut(managerForName) { mutableSetOf() } += packageManager
                }
            }
        }

        return result
    }
}

private class AnalyzerState(curationProvider: PackageCurationProvider) {
    private val builder = AnalyzerResultBuilder(curationProvider)
    private val scope = CoroutineScope(Dispatchers.Default)
    private val addMutex = Mutex()

    private val _finishedPackageManagersState = MutableStateFlow<Set<String>>(emptySet())
    val finishedPackageManagersState = _finishedPackageManagersState.asStateFlow()

    fun addResult(manager: PackageManager, result: PackageManagerResult) {
        scope.launch {
            // By convention, project ids must be of the type of the respective package manager. An exception
            // for this is Pub with Flutter, which internally calls Gradle.
            result.projectResults.forEach { (_, result) ->
                val invalidProjects = result.filterNot {
                    val projectType = it.project.id.type

                    projectType == manager.managerName ||
                            (manager.managerName == "Pub" && projectType == "Gradle")
                }

                require(invalidProjects.isEmpty()) {
                    val projectString = invalidProjects.joinToString { "'${it.project.id.toCoordinates()}'" }
                    "Projects $projectString must be of type '${manager.managerName}'."
                }
            }

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

    fun buildResult() = builder.build()
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
     * A [StateFlow] that updates with the list of already finished package managers.
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
            manager.log.info {
                "Waiting for the following package managers to complete: ${mustRunAfter.joinToString()}."
            }

            finishedPackageManagersState.first { finishedPackageManagers ->
                val remaining = mustRunAfter - finishedPackageManagers

                if (remaining.isNotEmpty()) {
                    manager.log.info {
                        "Still waiting for the following package managers to complete: ${remaining.joinToString()}."
                    }
                }

                remaining.isEmpty()
            }
        }

        run()
    }

    private suspend fun run() {
        manager.log.info { "Starting analysis." }

        withContext(Dispatchers.IO) {
            val result = manager.resolveDependencies(definitionFiles, labels)

            manager.log.info { "Finished analysis." }

            onResult(result)
        }
    }
}
