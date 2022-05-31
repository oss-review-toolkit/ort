/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult

import java.io.File
import java.lang.reflect.Modifier

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.Scanner
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.spdx.scanCodeLicenseTextDir

import org.reflections.Reflections

class RequirementsCommand : CliktCommand(help = "Check for the command line tools required by ORT.") {
    override fun run() {
        val reflections = Reflections("org.ossreviewtoolkit")
        val classes = reflections.getSubTypesOf(CommandLineTool::class.java)

        val allTools = mutableMapOf<String, MutableList<CommandLineTool>>()

        classes.filterNot {
            Modifier.isAbstract(it.modifiers) || it.isAnonymousClass || it.isLocalClass
        }.sortedBy { it.simpleName }.forEach {
            runCatching {
                val kotlinObject = it.kotlin.objectInstance

                var category = "Other tool"
                val instance = when {
                    kotlinObject != null -> {
                        log.debug { "$it is a Kotlin object." }
                        kotlinObject
                    }

                    PackageManager::class.java.isAssignableFrom(it) -> {
                        category = "PackageManager"
                        log.debug { "$it is a $category." }
                        it.getDeclaredConstructor(
                            String::class.java,
                            File::class.java,
                            AnalyzerConfiguration::class.java,
                            RepositoryConfiguration::class.java
                        ).newInstance(
                            "",
                            File(""),
                            AnalyzerConfiguration(allowDynamicVersions = false),
                            RepositoryConfiguration()
                        )
                    }

                    Scanner::class.java.isAssignableFrom(it) -> {
                        category = "Scanner"
                        log.debug { "$it is a $category." }
                        it.getDeclaredConstructor(
                            String::class.java,
                            ScannerConfiguration::class.java,
                            DownloaderConfiguration::class.java
                        ).newInstance("", ScannerConfiguration(), DownloaderConfiguration())
                    }

                    VersionControlSystem::class.java.isAssignableFrom(it) -> {
                        category = "VersionControlSystem"
                        log.debug { "$it is a $category." }
                        it.getDeclaredConstructor().newInstance()
                    }

                    else -> {
                        log.debug { "Trying to instantiate $it without any arguments." }
                        it.getDeclaredConstructor().newInstance()
                    }
                }

                if (instance.command().isNotEmpty()) {
                    allTools.getOrPut(category) { mutableListOf() } += instance
                }
            }.onFailure { e ->
                log.error { "There was an error instantiating $it: $e." }
                throw ProgramResult(1)
            }
        }

        // Toggle bits in here to denote the kind of error. Skip the first bit as status code 1 is already used above.
        var statusCode = 0

        allTools.forEach { (category, tools) ->
            println("${category}s:")

            tools.forEach { tool ->
                // TODO: State whether a tool can be bootstrapped, but that requires refactoring of CommandLineTool.
                val message = buildString {
                    val (prefix, suffix) = if (tool.isInPath() || File(tool.command()).isFile) {
                        runCatching {
                            val actualVersion = tool.getVersion()
                            runCatching {
                                val isRequiredVersion = tool.getVersionRequirement().let {
                                    it == CommandLineTool.ANY_VERSION || it.isSatisfiedBy(actualVersion)
                                }

                                if (isRequiredVersion) {
                                    Pair("\t* ", "Found version $actualVersion.")
                                } else {
                                    statusCode = statusCode or 2
                                    Pair("\t+ ", "Found version $actualVersion.")
                                }
                            }.getOrElse {
                                statusCode = statusCode or 2
                                Pair("\t+ ", "Found version '$actualVersion'.")
                            }
                        }.getOrElse {
                            statusCode = statusCode or 2
                            Pair("\t+ ", "Could not determine the version.")
                        }
                    } else {
                        // Tolerate scanners and Pub to be missing as they can be bootstrapped.
                        // Tolerate Yarn2 because it is provided in code repositories that use it.
                        if (category != "Scanner" && tool.javaClass.simpleName != "Pub"
                            && tool.javaClass.simpleName != "Yarn2"
                        ) {
                            statusCode = statusCode or 4
                        }

                        Pair("\t- ", "Tool not found.")
                    }

                    append(prefix)
                    append("${tool.javaClass.simpleName}: Requires '${tool.command()}' in ")

                    if (tool.getVersionRequirement() == CommandLineTool.ANY_VERSION) {
                        append("no specific version. ")
                    } else {
                        append("version ${tool.getVersionRequirement()}. ")
                    }

                    append(suffix)
                }

                println(message)
            }

            println()
        }

        println("Prefix legend:")
        println("\t- The tool was not found in the PATH environment.")
        println("\t+ The tool was found in the PATH environment, but not in the required version.")
        println("\t* The tool was found in the PATH environment in the required version.")

        println()
        if (scanCodeLicenseTextDir != null) {
            println("ScanCode license texts found in '$scanCodeLicenseTextDir'.")
        } else {
            println("ScanCode license texts not found.")
        }

        if (statusCode != 0) {
            println()
            println("Not all tools were found in their required versions.")
            throw ProgramResult(statusCode)
        }
    }
}
