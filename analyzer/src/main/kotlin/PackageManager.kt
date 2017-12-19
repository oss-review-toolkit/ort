/*
 * Copyright (c) 2017 HERE Europe B.V.
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

import com.here.ort.analyzer.managers.*
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Project
import com.here.ort.utils.collectMessages
import com.here.ort.utils.log

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

import kotlin.system.measureTimeMillis

typealias ManagedProjectFiles = Map<PackageManagerFactory<PackageManager>, List<File>>
typealias ResolutionResult = MutableMap<File, AnalyzerResult>

/**
 * A class representing a package manager that handles software dependencies.
 */
abstract class PackageManager {
    companion object {
        @JvmStatic
        val HTTP_CACHE_PATH = "analyzer/cache/http"

        /**
         * The prioritized list of all available package managers. This needs to be initialized lazily to ensure the
         * referred objects, which derive from this class, exist.
         */
        val ALL by lazy {
            listOf(
                    Gradle,
                    Maven,
                    SBT,
                    NPM,
                    // TODO: CocoaPods,
                    // TODO: Godep,
                    // TODO: Bower,
                    PIP
                    // TODO: Bundler
            )
        }

        /**
         * Recursively search for files managed by a package manager.
         *
         * @param directory The root directory to search for managed files.
         * @param packageManagers A list of package managers to use, defaults to [ALL].
         */
        fun findManagedFiles(directory: File, packageManagers: List<PackageManagerFactory<PackageManager>> = ALL)
                : ManagedProjectFiles {
            require(directory.isDirectory) {
                "The provided path is not a directory: ${directory.absolutePath}"
            }

            val result = mutableMapOf<PackageManagerFactory<PackageManager>, MutableList<File>>()

            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                    val filesInDir = dir.toFile().listFiles()

                    packageManagers.forEach { manager ->
                        val matches = manager.matchersForDefinitionFiles.mapNotNull { glob ->
                            filesInDir.find { file ->
                                glob.matches(file.toPath())
                            }
                        }

                        if (matches.isNotEmpty()) {
                            result.getOrPut(manager) { mutableListOf() }.add(matches.first())
                        }
                    }

                    return FileVisitResult.CONTINUE
                }
            })

            return result
        }
    }

    /**
     * Return the name of the package manager's command line application. As the preferred command might depend on the
     * working directory it needs to be provided.
     */
    abstract fun command(workingDir: File): String

    /**
     * Return a tree of resolved dependencies (not necessarily declared dependencies, in case conflicts were resolved)
     * for each provided path.
     */
    open fun resolveDependencies(projectDir: File, definitionFiles: List<File>): ResolutionResult {
        val result = mutableMapOf<File, AnalyzerResult>()

        prepareResolution(definitionFiles).forEach { definitionFile ->
            val workingDir = definitionFile.parentFile

            println("Resolving ${javaClass.simpleName} dependencies in '$workingDir'...")

            val elapsed = measureTimeMillis {
                try {
                    resolveDependencies(projectDir, workingDir, definitionFile)?.let {
                        result[definitionFile] = it
                    }
                } catch (e: Exception) {
                    if (Main.stacktrace) {
                        e.printStackTrace()
                    }

                    result[definitionFile] = AnalyzerResult(Main.allowDynamicVersions, Project.EMPTY,
                            sortedSetOf(), e.collectMessages())

                    log.error { "Resolving dependencies in '${workingDir.name}' failed with: ${e.message}" }
                }
            }

            log.info {
                "Resolving ${javaClass.simpleName} dependencies in '${workingDir.name}' took ${elapsed / 1000}s."
            }
        }

        return result
    }

    /**
     * Optional preparation step for dependency resolution, like checking for prerequisites or mapping
     * [definitionFiles].
     */
    protected open fun prepareResolution(definitionFiles: List<File>): List<File> = definitionFiles

    /**
     * Resolve dependencies for a single [definitionFile], returning the [AnalyzerResult].
     */
    protected open fun resolveDependencies(projectDir: File, workingDir: File, definitionFile: File): AnalyzerResult? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
