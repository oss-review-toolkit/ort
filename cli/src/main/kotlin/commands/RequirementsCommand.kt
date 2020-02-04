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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError

import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.scanner.Scanner
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.log

import java.io.File
import java.lang.reflect.Modifier

import org.reflections.Reflections

class RequirementsCommand : CliktCommand(name = "requirements", help = "List the required command line tools.") {
    override fun run() {
        val reflections = Reflections("com.here.ort")
        val classes = reflections.getSubTypesOf(CommandLineTool::class.java)

        val allTools = mutableMapOf<String, MutableList<CommandLineTool>>()

        classes.filterNot {
            Modifier.isAbstract(it.modifiers) || it.isAnonymousClass || it.isLocalClass
        }.sortedBy { it.simpleName }.forEach {
            @Suppress("TooGenericExceptionCaught")
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
                        it.getDeclaredConstructor(
                            String::class.java,
                            File::class.java,
                            AnalyzerConfiguration::class.java,
                            RepositoryConfiguration::class.java
                        ).newInstance(
                            "",
                            File(""),
                            AnalyzerConfiguration(ignoreToolVersions = false, allowDynamicVersions = false),
                            RepositoryConfiguration()
                        )
                    }

                    Scanner::class.java.isAssignableFrom(it) -> {
                        key = "Scanner"
                        log.debug { "$it is a $key." }
                        it.getDeclaredConstructor(String::class.java, ScannerConfiguration::class.java)
                            .newInstance("", ScannerConfiguration())
                    }

                    VersionControlSystem::class.java.isAssignableFrom(it) -> {
                        key = "VersionControlSystem"
                        log.debug { "$it is a $key." }
                        it.getDeclaredConstructor().newInstance()
                    }

                    else -> {
                        log.debug { "Trying to instantiate $it without any arguments." }
                        it.getDeclaredConstructor().newInstance()
                    }
                }

                if (instance.command().isNotEmpty()) {
                    allTools.getOrPut(key) { mutableListOf() } += instance
                }
            } catch (e: Exception) {
                throw UsageError("There was an error instantiating $it: $e.", statusCode = 1)
            }
        }

        allTools.forEach { (category, tools) ->
            println("${category}s:")
            tools.forEach { tool ->
                // TODO: State which version was found, and whether it could be bootstrapped, but that requires
                //       refactoring of CommandLineTool.
                val message = buildString {
                    if (tool.isInPath()) append("\t* ") else append("\t- ")

                    append("${tool.javaClass.simpleName}: Requires '${tool.command()}' in ")
                    if (tool.getVersionRequirement().toString() == CommandLineTool.ANY_VERSION.toString()) {
                        append("no specific version.")
                    } else {
                        append("version ${tool.getVersionRequirement()}.")
                    }
                }

                println(message)
            }
        }

        println("Legend:")
        println("\tA '-' prefix means that the tool was _not_ found in the PATH environment.")
        println("\tA '*' prefix means that _some_ version of the tool was found in the PATH environment.")
    }
}
