/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import ch.frankel.slf4k.*

import com.here.ort.analyzer.managers.Unmanaged
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.AnalyzerResultBuilder
import com.here.ort.model.AnalyzerRun
import com.here.ort.model.Environment
import com.here.ort.model.OrtResult
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.Repository
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.readValue
import com.here.ort.utils.log

import java.io.File

const val TOOL_NAME = "analyzer"
const val HTTP_CACHE_PATH = "$TOOL_NAME/cache/http"

/**
 * The class to run the analysis. The signatures of public functions in this class define the library API.
 */
class Analyzer(private val config: AnalyzerConfiguration) {
    fun analyze(
            absoluteProjectPath: File,
            packageManagers: List<PackageManagerFactory> = PackageManager.ALL,
            packageCurationsFile: File? = null,
            repositoryConfigurationFile: File? = null
    ): OrtResult {
        val actualRepositoryConfigurationFile = repositoryConfigurationFile ?: File(absoluteProjectPath, ".ort.yml")

        val repositoryConfiguration = if (actualRepositoryConfigurationFile.isFile) {
            actualRepositoryConfigurationFile.readValue()
        } else {
            RepositoryConfiguration(null)
        }

        // Map files by the package manager factory that manages them.
        val factoryFiles = if (packageManagers.size == 1 && absoluteProjectPath.isFile) {
            // If only one package manager is activated, treat the given path as definition file for that package
            // manager despite its name.
            mutableMapOf(packageManagers.first() to listOf(absoluteProjectPath))
        } else {
            PackageManager.findManagedFiles(absoluteProjectPath, packageManagers).toMutableMap()
        }

        val hasDefinitionFileInRootDirectory = factoryFiles.values.flatten().any {
            it.parentFile.absoluteFile == absoluteProjectPath
        }

        if (factoryFiles.isEmpty() || !hasDefinitionFileInRootDirectory) {
            factoryFiles[Unmanaged.Factory()] = listOf(absoluteProjectPath)
        }

        // Instantiate the managers via their factories.
        val managedFiles = factoryFiles.mapNotNull { (factory, files) ->
            val manager = factory.create(config, repositoryConfiguration)
            val filteredFiles = manager.prepareResolution(files)
            Pair(manager, filteredFiles).takeIf { filteredFiles.isNotEmpty() }
        }.toMap()

        if (log.isInfoEnabled) {
            // Log the summary of projects found per package manager.
            managedFiles.forEach { manager, files ->
                // No need to use curly-braces-syntax for logging here as the log level check is already done above.
                log.info("$manager projects found in:")
                log.info(files.joinToString("\n") { file ->
                    "\t${file.toRelativeString(absoluteProjectPath).let { if (it.isEmpty()) "." else it }}"
                })
            }
        }

        val analyzerResultBuilder = AnalyzerResultBuilder()

        // Resolve dependencies per package manager.
        managedFiles.forEach { manager, files ->
            val results = manager.resolveDependencies(absoluteProjectPath, files)

            val curatedResults = packageCurationsFile?.let {
                val provider = YamlFilePackageCurationProvider(it)
                results.mapValues { entry ->
                    ProjectAnalyzerResult(
                            project = entry.value.project,
                            errors = entry.value.errors,
                            packages = entry.value.packages.map { curatedPackage ->
                                val curations = provider.getCurationsFor(curatedPackage.pkg.id)
                                curations.fold(curatedPackage) { cur, packageCuration ->
                                    log.debug {
                                        "Applying curation '$packageCuration' to package '${curatedPackage.pkg.id}'."
                                    }
                                    packageCuration.apply(cur)
                                }
                            }.toSortedSet()
                    )
                }
            } ?: results

            curatedResults.forEach { _, analyzerResult ->
                analyzerResultBuilder.addResult(analyzerResult)
            }
        }

        val vcs = VersionControlSystem.getCloneInfo(absoluteProjectPath)
        val repository = Repository(vcs, vcs.normalize(), repositoryConfiguration)

        val run = AnalyzerRun(Environment(), config, analyzerResultBuilder.build())

        return OrtResult(repository, run)
    }
}
