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

package com.here.ort.analyzer

import com.here.ort.analyzer.managers.Unmanaged
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.AnalyzerResultBuilder
import com.here.ort.model.AnalyzerRun
import com.here.ort.model.Environment
import com.here.ort.model.OrtResult
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.Repository
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.readValue
import com.here.ort.utils.NamedThreadFactory
import com.here.ort.utils.ORT_CONFIG_FILENAME
import com.here.ort.utils.log

import java.io.File
import java.time.Instant
import java.util.concurrent.Executors

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

const val TOOL_NAME = "analyzer"
const val HTTP_CACHE_PATH = "$TOOL_NAME/cache/http"

/**
 * The class to run the analysis. The signatures of public functions in this class define the library API.
 */
class Analyzer(private val config: AnalyzerConfiguration) {
    fun analyze(
        absoluteProjectPath: File,
        packageManagers: List<PackageManagerFactory> = PackageManager.ALL,
        curationProvider: PackageCurationProvider? = null,
        repositoryConfigurationFile: File? = null
    ): OrtResult {
        val startTime = Instant.now()

        val actualRepositoryConfigurationFile = repositoryConfigurationFile
            ?: File(absoluteProjectPath, ORT_CONFIG_FILENAME)

        val repositoryConfiguration = if (actualRepositoryConfigurationFile.isFile) {
            log.info { "Using configuration file '${actualRepositoryConfigurationFile.absolutePath}'." }

            actualRepositoryConfigurationFile.readValue()
        } else {
            RepositoryConfiguration()
        }

        log.debug { "Using the following configuration settings:\n$repositoryConfiguration" }

        // Map files by the package manager factory that manages them.
        val factoryFiles = if (packageManagers.size == 1 && absoluteProjectPath.isFile) {
            // If only one package manager is activated, treat the given path as definition file for that package
            // manager despite its name.
            mutableMapOf(packageManagers.first() to listOf(absoluteProjectPath))
        } else {
            PackageManager.findManagedFiles(absoluteProjectPath, packageManagers).toMutableMap()
        }

        val managedFiles = factoryFiles.mapNotNull { (factory, files) ->
            val manager = factory.create(absoluteProjectPath, config, repositoryConfiguration)
            val mappedFiles = manager.mapDefinitionFiles(files)
            Pair(manager, mappedFiles).takeIf { mappedFiles.isNotEmpty() }
        }.toMap(mutableMapOf())

        val hasDefinitionFileInRootDirectory = managedFiles.values.flatten().any {
            it.parentFile.absoluteFile == absoluteProjectPath
        }

        if (factoryFiles.isEmpty() || !hasDefinitionFileInRootDirectory) {
            Unmanaged.Factory().create(absoluteProjectPath, config, repositoryConfiguration).let {
                managedFiles[it] = listOf(absoluteProjectPath)
            }
        }

        if (log.delegate.isInfoEnabled) {
            // Log the summary of projects found per package manager.
            managedFiles.forEach { (manager, files) ->
                // No need to use curly-braces-syntax for logging here as the log level check is already done above.
                log.info("${manager.managerName} projects found in:")
                files.forEach { file ->
                    log.info("\t${file.toRelativeString(absoluteProjectPath).takeIf { it.isNotEmpty() } ?: "."}")
                }
            }
        }

        // Resolve dependencies per package manager.
        val analyzerResult = runBlocking { analyzeInParallel(managedFiles, curationProvider) }

        val workingTree = VersionControlSystem.forDirectory(absoluteProjectPath)
        val vcs = workingTree?.getInfo() ?: VcsInfo.EMPTY
        val nestedVcs = workingTree?.getNested()?.filter { (path, _) ->
            // Only include nested VCS if they are part of the analyzed directory.
            workingTree.getRootPath().resolve(path).startsWith(absoluteProjectPath)
        }.orEmpty()
        val repository = Repository(vcs = vcs, nestedRepositories = nestedVcs, config = repositoryConfiguration)

        val endTime = Instant.now()

        val run = AnalyzerRun(startTime, endTime, Environment(), config, analyzerResult)

        return OrtResult(repository, run)
    }

    private suspend fun analyzeInParallel(
        managedFiles: Map<PackageManager, List<File>>,
        curationProvider: PackageCurationProvider?
    ): AnalyzerResult {
        val analysisDispatcher = Executors.newFixedThreadPool(5, NamedThreadFactory(TOOL_NAME)).asCoroutineDispatcher()
        val analyzerResultBuilder = AnalyzerResultBuilder()

        analysisDispatcher.use { dispatcher ->
            coroutineScope {
                managedFiles.map { (manager, files) ->
                    async {
                        withContext(dispatcher) {
                            val results = manager.resolveDependencies(files)

                            // By convention, project ids must be of the type of the respective package manager.
                            results.forEach { (_, result) ->
                                require(result.project.id.type == manager.managerName) {
                                    "Project '${result.project.id.toCoordinates()}' must be of type " +
                                            "'${manager.managerName}'."
                                }
                            }

                            curationProvider?.let { provider ->
                                results.mapValues { entry ->
                                    ProjectAnalyzerResult(
                                        project = entry.value.project,
                                        errors = entry.value.errors,
                                        packages = entry.value.packages.map { curatedPackage ->
                                            val curations = provider.getCurationsFor(curatedPackage.pkg.id)
                                            curations.fold(curatedPackage) { cur, packageCuration ->
                                                log.debug {
                                                    "Applying curation '$packageCuration' to package " +
                                                            "'${curatedPackage.pkg.id.toCoordinates()}'."
                                                }
                                                packageCuration.apply(cur)
                                            }
                                        }.toSortedSet()
                                    )
                                }
                            } ?: results
                        }
                    }
                }.forEach {
                    it.await().forEach { (_, analyzerResult) ->
                        analyzerResultBuilder.addResult(analyzerResult)
                    }
                }
            }
        }

        return analyzerResultBuilder.build()
    }
}
