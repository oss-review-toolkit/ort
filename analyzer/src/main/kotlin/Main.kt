/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException

import com.fasterxml.jackson.databind.ObjectMapper

import com.here.ort.analyzer.managers.Unmanaged
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.CuratedPackage
import com.here.ort.model.MergedResultsBuilder
import com.here.ort.model.OutputFormat
import com.here.ort.model.VcsInfo
import com.here.ort.utils.PARAMETER_ORDER_HELP
import com.here.ort.utils.PARAMETER_ORDER_LOGGING
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.log
import com.here.ort.utils.printStackTrace
import com.here.ort.utils.safeMkdirs

import java.io.File

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {
    const val TOOL_NAME = "analyzer"
    const val HTTP_CACHE_PATH = "$TOOL_NAME/cache/http"

    private class PackageManagerConverter : IStringConverter<PackageManagerFactory<PackageManager>> {
        companion object {
            // Map upper-cased package manager class names to their instances.
            val PACKAGE_MANAGER_NAMES = PackageManager.ALL.associateBy { it.toString().toUpperCase() }
        }

        override fun convert(name: String): PackageManagerFactory<PackageManager> {
            return PACKAGE_MANAGER_NAMES[name.toUpperCase()]
                    ?: throw ParameterException("Package managers must be contained in ${PACKAGE_MANAGER_NAMES.keys}.")
        }
    }

    @Parameter(description = "A list of package managers to activate.",
            names = ["--package-managers", "-m"],
            converter = PackageManagerConverter::class,
            order = PARAMETER_ORDER_OPTIONAL)
    private var packageManagers = PackageManager.ALL

    @Parameter(description = "The project directory to scan.",
            names = ["--input-dir", "-i"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var inputDir: File

    @Parameter(description = "The directory to write dependency information to.",
            names = ["--output-dir", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(
            description = "Merge all results into a single results file. The individual scan results files " +
                    "for each build file will still be created.",
            names = ["--merge-results"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var createMergedResult = false

    @Parameter(description = "The data format used for dependency information.",
            names = ["--output-format", "-f"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var outputFormat = OutputFormat.YAML

    @Suppress("LateinitUsage")
    private lateinit var mapper: ObjectMapper

    @Parameter(description = "Ignore versions of required tools. NOTE: This may lead to erroneous results.",
            names = ["--ignore-versions"],
            order = PARAMETER_ORDER_OPTIONAL)
    var ignoreVersions = false

    @Parameter(description = "Allow dynamic versions of dependencies. This can result in unstable results when " +
            "dependencies use version ranges. This option only affects package managers that support lock files, " +
            "like NPM.",
            names = ["--allow-dynamic-versions"],
            order = PARAMETER_ORDER_OPTIONAL)
    var allowDynamicVersions = false

    @Parameter(description = "A YAML file that contains package curation data.",
            names = ["--package-curations-file"],
            order = PARAMETER_ORDER_OPTIONAL)
    var packageCurationsFile: File? = null

    @Parameter(description = "Enable info logging.",
            names = ["--info"],
            order = PARAMETER_ORDER_LOGGING)
    private var info = false

    @Parameter(description = "Enable debug logging and keep any temporary files.",
            names = ["--debug"],
            order = PARAMETER_ORDER_LOGGING)
    private var debug = false

    @Parameter(description = "Print out the stacktrace for all exceptions.",
            names = ["--stacktrace"],
            order = PARAMETER_ORDER_LOGGING)
    private var stacktrace = false

    @Parameter(description = "Display the command line help.",
            names = ["--help", "-h"],
            help = true,
            order = PARAMETER_ORDER_HELP)
    private var help = false

    private fun writeResultFile(projectRoot: File, currentPath: File, outputRoot: File, result: AnalyzerResult): File {
        // Mirror the directory structure from the project in the output.
        val currentDir = if (currentPath.isFile) currentPath.parentFile else currentPath
        val outputDir = File(outputRoot, currentDir.toRelativeString(projectRoot)).apply { safeMkdirs() }
        val outputFile = File(outputDir, currentPath.name.replace('.', '-') +
                "-dependencies." + outputFormat.fileExtension)

        println("Writing results for\n\t$currentPath\nto\n\t$outputFile")
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, result)
        println("done.")

        return outputFile
    }

    /**
     * The entry point for the application.
     *
     * @param args The list of application arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val jc = JCommander(this)
        jc.parse(*args)
        jc.programName = TOOL_NAME

        if (info) {
            log.level = ch.qos.logback.classic.Level.INFO
        }

        if (debug) {
            log.level = ch.qos.logback.classic.Level.DEBUG
        }

        if (help) {
            jc.usage()
            exitProcess(0)
        }

        // Make the parameter globally available.
        printStackTrace = stacktrace

        val absoluteOutputPath = outputDir.absoluteFile
        if (absoluteOutputPath.exists()) {
            log.error { "The output directory '$absoluteOutputPath' must not exist yet." }
            exitProcess(1)
        }

        mapper = outputFormat.mapper

        println("The following package managers are activated:")
        println("\t" + packageManagers.joinToString(", "))

        val absoluteProjectPath = inputDir.absoluteFile
        println("Scanning project path:\n\t$absoluteProjectPath")

        // Map of files managed by the respective package manager.
        val managedDefinitionFiles = if (packageManagers.size == 1 && absoluteProjectPath.isFile) {
            // If only one package manager is activated, treat the given path as definition file for that package
            // manager despite its name.
            mutableMapOf(packageManagers.first() to listOf(absoluteProjectPath))
        } else {
            PackageManager.findManagedFiles(absoluteProjectPath, packageManagers).toMutableMap()
        }

        val vcs = VersionControlSystem.forDirectory(absoluteProjectPath)

        val hasDefinitionFileInRootDirectory = managedDefinitionFiles.values.flatten().any {
            it.parentFile.absoluteFile == absoluteProjectPath
        }

        if (managedDefinitionFiles.isEmpty() || !hasDefinitionFileInRootDirectory) {
            managedDefinitionFiles[Unmanaged] = listOf(absoluteProjectPath)
        }

        // Print a summary of all projects found per package manager.
        managedDefinitionFiles.forEach { manager, files ->
            println("$manager projects found in:")
            println(files.joinToString("\n") {
                "\t${it.toRelativeString(absoluteProjectPath).let { if (it.isEmpty()) "." else it}}"
            })
        }

        val failedAnalysis = sortedSetOf<String>()

        val mergedResultsBuilder = if (createMergedResult) {
            MergedResultsBuilder(allowDynamicVersions, absoluteProjectPath,
                    vcs?.getInfo(absoluteProjectPath) ?: VcsInfo.EMPTY)
        } else {
            null
        }

        // Resolve dependencies per package manager.
        managedDefinitionFiles.forEach { manager, files ->
            // Print the list of dependencies.
            val results = manager.create().resolveDependencies(files)

            val curatedResults = packageCurationsFile?.let {
                val provider = YamlFilePackageCurationProvider(it)
                results.mapValues { entry ->
                    AnalyzerResult(
                            allowDynamicVersions = entry.value.allowDynamicVersions,
                            project = entry.value.project,
                            errors = entry.value.errors,
                            packages = entry.value.packages.map { pkg ->
                                val curations = provider.getCurationsFor(pkg.id)
                                curations.fold(CuratedPackage(pkg, emptyList())) { cur, packageCuration ->
                                    log.debug { "Applying curation '$packageCuration' to package '${pkg.id}'." }
                                    packageCuration.apply(cur)
                                }
                            }.map { it.pkg }.toSortedSet()
                    )
                }
            } ?: results

            curatedResults.forEach { definitionFile, analyzerResult ->
                val resultFile = writeResultFile(absoluteProjectPath, definitionFile, absoluteOutputPath,
                        analyzerResult)
                mergedResultsBuilder?.addResult(resultFile, analyzerResult)
                if (analyzerResult.hasErrors()) {
                    failedAnalysis.add(definitionFile.absolutePath)
                }
            }
        }

        mergedResultsBuilder?.build()?.let {
            val outputFile = File(absoluteOutputPath, "all-dependencies." + outputFormat.fileExtension)

            println("Writing merged results\nto\n\t$outputFile")
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, it)
            println("done.")
        }

        if (failedAnalysis.isNotEmpty()) {
            log.error {
                "Analysis for these projects did not complete successfully:\n" +
                        failedAnalysis.joinToString(separator = "\n")
            }
        }
    }
}
