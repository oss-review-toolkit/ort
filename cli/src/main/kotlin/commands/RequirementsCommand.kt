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

package com.here.ort.commands

import ch.frankel.slf4k.*

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameters

import com.here.ort.CommandWithHelp
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.scanner.Scanner
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.log

import org.reflections.Reflections

import java.lang.reflect.Modifier

@Parameters(commandNames = ["requirements"], commandDescription = "List the required command line tools.")
object RequirementsCommand : CommandWithHelp() {
    override fun runCommand(jc: JCommander): Int {
        val reflections = Reflections("com.here.ort")
        val classes = reflections.getSubTypesOf(CommandLineTool::class.java)

        val allTools = mutableMapOf<String, MutableList<CommandLineTool>>()

        classes.filterNot {
            Modifier.isAbstract(it.modifiers) || it.isAnonymousClass || it.isLocalClass
        }.sortedBy { it.simpleName }.forEach {
            try {
                val kotlinObject = it.kotlin.objectInstance

                var key = "Other tool"
                val instance = when {
                    kotlinObject != null -> {
                        log.debug { "$it is a Kotlin object." }
                        kotlinObject
                    }

                    PackageManager::class.java.isAssignableFrom(it) -> {
                        key = "PackageManager"
                        log.debug { "$it is a $key." }
                        it.getDeclaredConstructor(AnalyzerConfiguration::class.java,
                                RepositoryConfiguration::class.java).newInstance(AnalyzerConfiguration(false, false),
                                RepositoryConfiguration())
                    }

                    Scanner::class.java.isAssignableFrom(it) -> {
                        key = "Scanner"
                        log.debug { "$it is a $key." }
                        it.getDeclaredConstructor(ScannerConfiguration::class.java)
                                .newInstance(ScannerConfiguration())
                    }

                    VersionControlSystem::class.java.isAssignableFrom(it) -> {
                        key = "VersionControlSystem"
                        log.debug { "$it is a $key." }
                        it.getDeclaredConstructor().newInstance()
                    }

                    else -> {
                        log.debug { "Trying to instanciate $it without any arguments." }
                        it.getDeclaredConstructor().newInstance()
                    }
                }

                if (instance.command().isNotEmpty()) {
                    allTools.getOrPut(key) { mutableListOf() } += instance
                }
            } catch (e: Exception) {
                log.error { "There was an error instanciating $it: $e." }
            }
        }

        allTools.forEach { category, tools ->
            println("${category}s:")
            tools.forEach { tool ->
                val message = if (tool.getVersionRequirement().toString() == CommandLineTool.ANY_VERSION.toString()) {
                    "${tool.javaClass.simpleName} requires '${tool.command()}' in no specific version."
                } else {
                    "${tool.javaClass.simpleName} has ${tool.getVersionRequirement()} on the version of " +
                            "'${tool.command()}'."
                }

                // TODO: State which version was found, and whether it could be bootstrapped, but that requires
                // refactoring of CommandLineTool.
                val (prefix, suffix) = if (tool.isInPath()) {
                    Pair("\t* ", " (Some version was found in the PATH environment.)")
                } else {
                    Pair("\t- ", "")
                }

                println(prefix + message + suffix)
            }
        }

        println("Legend:")
        println("\tA '-' prefix means that the tool was not found in the PATH environment.")
        println("\tA '*' prefix means that some version of the tool was found in the PATH environment.")

        return 0
    }
}
