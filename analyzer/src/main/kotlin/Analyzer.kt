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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.analyzer.managers.Unmanaged
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.core.Environment
import org.ossreviewtoolkit.utils.core.log

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

        log.debug { "Using the following configuration settings:\n$repositoryConfiguration" }

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

        val hasDefinitionFileInRootDirectory = managedFiles.values.flatten().any {
            it.parentFile.absoluteFile == absoluteProjectPath
        }

        val unmanagedFactory = packageManagers.find { it is Unmanaged.Factory }
        if (unmanagedFactory != null && (factoryFiles.isEmpty() || !hasDefinitionFileInRootDirectory)) {
            unmanagedFactory.create(absoluteProjectPath, config, repositoryConfiguration).let {
                managedFiles[it] = listOf(absoluteProjectPath)
            }
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
        val analyzerResultBuilder = AnalyzerResultBuilder(curationProvider)

        runBlocking(Dispatchers.IO) {
            managedFiles.map { (manager, files) ->
                async {
                    val results = manager.resolveDependencies(files, labels)

                    // By convention, project ids must be of the type of the respective package manager. An exception
                    // for this is Pub with Flutter, which internally calls Gradle.
                    results.projectResults.forEach { (_, result) ->
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

                    manager to results
                }
            }.forEach { deferredResult ->
                val (manager, managerResult) = deferredResult.await()
                managerResult.projectResults.values.flatten().forEach { analyzerResultBuilder.addResult(it) }
                managerResult.dependencyGraph?.let {
                    analyzerResultBuilder.addDependencyGraph(manager.managerName, it)
                        .addPackages(managerResult.sharedPackages)
                }
            }
        }

        return analyzerResultBuilder.build()
    }
}
