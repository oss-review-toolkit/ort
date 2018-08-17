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
import com.here.ort.model.ExcludesMarker
import com.here.ort.model.ExcludesRemover
import com.here.ort.model.OrtResult
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.Repository
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.readValue
import com.here.ort.utils.log

import java.io.File

class Analyzer {
    fun analyze(analyzerConfiguration: AnalyzerConfiguration, absoluteProjectPath: File,
                packageManagers: List<PackageManagerFactory<PackageManager>> = PackageManager.ALL,
                packageCurationsFile: File? = null
    ): OrtResult {
        val repositoryConfigurationFile = File(absoluteProjectPath, ".ort.yml")

        val repositoryConfiguration = if (repositoryConfigurationFile.isFile) {
            repositoryConfigurationFile.readValue(RepositoryConfiguration::class.java)
        } else {
            RepositoryConfiguration(null)
        }

        // Map of files managed by the respective package manager.
        var managedDefinitionFiles = if (packageManagers.size == 1 && absoluteProjectPath.isFile) {
            // If only one package manager is activated, treat the given path as definition file for that package
            // manager despite its name.
            mutableMapOf(packageManagers.first() to listOf(absoluteProjectPath))
        } else {
            PackageManager.findManagedFiles(absoluteProjectPath, packageManagers).toMutableMap()
        }

        if (analyzerConfiguration.removeExcludesFromResult) {
            managedDefinitionFiles = filterExcludedProjects(managedDefinitionFiles, repositoryConfiguration)
        }

        val hasDefinitionFileInRootDirectory = managedDefinitionFiles.values.flatten().any {
            it.parentFile.absoluteFile == absoluteProjectPath
        }

        if (managedDefinitionFiles.isEmpty() || !hasDefinitionFileInRootDirectory) {
            managedDefinitionFiles[Unmanaged] = listOf(absoluteProjectPath)
        }

        if (log.isInfoEnabled) {
            // Log the summary of projects found per package manager.
            managedDefinitionFiles.forEach { manager, files ->
                // No need to use curly-braces-syntax for logging here as the log level check is already done above.
                log.info("$manager projects found in:")
                log.info(files.joinToString("\n") {
                    "\t${it.toRelativeString(absoluteProjectPath).let { if (it.isEmpty()) "." else it }}"
                })
            }
        }

        val vcs = VersionControlSystem.getCloneInfo(absoluteProjectPath)
        val analyzerResultBuilder = AnalyzerResultBuilder()

        // Resolve dependencies per package manager.
        managedDefinitionFiles.forEach { manager, files ->
            val results = manager.create(analyzerConfiguration, repositoryConfiguration)
                    .resolveDependencies(absoluteProjectPath, files)

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

        val repository = Repository(vcs, vcs.normalize(), repositoryConfiguration)

        val analyzerResult = analyzerResultBuilder.build().let { analyzerResult ->
            repositoryConfiguration.excludes?.let { excludes ->
                val excludesProcessor = if (analyzerConfiguration.removeExcludesFromResult) {
                    ExcludesRemover(excludes)
                } else {
                    ExcludesMarker(excludes)
                }

                excludesProcessor.postProcess(analyzerResult)
            } ?: analyzerResult
        }

        val run = AnalyzerRun(Environment(), analyzerConfiguration, analyzerResult)

        return OrtResult(repository, run)
    }

    private fun filterExcludedProjects(
            managedDefinitionFiles: MutableMap<PackageManagerFactory<PackageManager>, List<File>>,
            repositoryConfiguration: RepositoryConfiguration
    ): MutableMap<PackageManagerFactory<PackageManager>, List<File>> {
        val excludedProjects = repositoryConfiguration.excludes?.projects?.filter { it.exclude } ?: emptyList()
        val excludedDefinitionFiles = excludedProjects.map { it.path }

        val definitionFilesToRemove = managedDefinitionFiles.flatMap { it.value }.filter { definitionFile ->
            val definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path
            definitionFilePath in excludedDefinitionFiles
        }

        require(definitionFilesToRemove.size == excludedDefinitionFiles.size) {
            "The following definition files are configured to be excluded in .ort.yml, but do not exist in the " +
                    "repository:\n${(excludedDefinitionFiles - definitionFilesToRemove).joinToString("\n")}"
        }

        log.info {
            "The following definition files are configured to be excluded in .ort.yml:\n" +
                    definitionFilesToRemove.joinToString("\n") { it.invariantSeparatorsPath }
        }

        val filteredManagedDefinitionFiles = mutableMapOf<PackageManagerFactory<PackageManager>, List<File>>()
        managedDefinitionFiles.forEach { packageManager, definitionFiles ->
            val filteredDefinitionFiles = definitionFiles - definitionFilesToRemove
            if (filteredDefinitionFiles.isNotEmpty()) {
                filteredManagedDefinitionFiles[packageManager] = filteredDefinitionFiles
            }
        }

        return filteredManagedDefinitionFiles
    }
}
