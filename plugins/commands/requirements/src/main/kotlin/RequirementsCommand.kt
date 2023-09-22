/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.commands.requirements

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.mordant.rendering.Theme

import java.io.File
import java.lang.reflect.Modifier

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.scanner.CommandLinePathScannerWrapper
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.spdx.scanCodeLicenseTextDir

import org.reflections.Reflections

import org.semver4j.Semver

private val DANGER_PREFIX = "\t${Theme.Default.danger("-")} "
private val WARNING_PREFIX = "\t${Theme.Default.warning("+")} "
private val SUCCESS_PREFIX = "\t${Theme.Default.success("*")} "

class RequirementsCommand : OrtCommand(
    name = "requirements",
    help = "Check for the command line tools required by ORT."
) {
    private companion object : Logging

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
                        logger.debug { "$it is a Kotlin object." }
                        kotlinObject
                    }

                    PackageManager::class.java.isAssignableFrom(it) -> {
                        category = "PackageManager"
                        logger.debug { "$it is a $category." }
                        it.getDeclaredConstructor(
                            String::class.java,
                            File::class.java,
                            AnalyzerConfiguration::class.java,
                            RepositoryConfiguration::class.java
                        ).newInstance(
                            "",
                            File(""),
                            AnalyzerConfiguration(),
                            RepositoryConfiguration()
                        )
                    }

                    CommandLinePathScannerWrapper::class.java.isAssignableFrom(it) -> {
                        category = "Scanner"
                        logger.debug { "$it is a $category." }
                        it.getDeclaredConstructor(
                            String::class.java,
                            ScannerConfiguration::class.java
                        ).newInstance("", ScannerConfiguration())
                    }

                    VersionControlSystem::class.java.isAssignableFrom(it) -> {
                        category = "VersionControlSystem"
                        logger.debug { "$it is a $category." }
                        it.getDeclaredConstructor().newInstance()
                    }

                    else -> {
                        logger.debug { "Trying to instantiate $it without any arguments." }
                        it.getDeclaredConstructor().newInstance()
                    }
                }

                if (instance.command().isNotEmpty()) {
                    allTools.getOrPut(category) { mutableListOf() } += instance
                }
            }.onFailure { e ->
                logger.error { "There was an error instantiating $it: $e." }
                throw ProgramResult(1)
            }
        }

        // Toggle bits in here to denote the kind of error. Skip the first bit as status code 1 is already used above.
        var statusCode = 0

        allTools.forEach { (category, tools) ->
            echo(Theme.Default.info("${category}s:"))

            tools.forEach { tool ->
                val message = buildString {
                    val (prefix, suffix) = if (tool.isInPath() || File(tool.command()).isFile) {
                        runCatching {
                            val actualVersion = tool.getVersion()
                            runCatching {
                                val isRequiredVersion = tool.getVersionRequirement().let {
                                    Semver.coerce(actualVersion)?.satisfies(it) == true
                                }

                                if (isRequiredVersion) {
                                    Pair(SUCCESS_PREFIX, "Found version $actualVersion.")
                                } else {
                                    statusCode = statusCode or 2
                                    Pair(WARNING_PREFIX, "Found version $actualVersion.")
                                }
                            }.getOrElse {
                                statusCode = statusCode or 2
                                Pair(WARNING_PREFIX, "Found version '$actualVersion'.")
                            }
                        }.getOrElse {
                            if (!tool.getVersionRequirement().isSatisfiedByAny) {
                                statusCode = statusCode or 2
                            }

                            Pair(WARNING_PREFIX, "Could not determine the version.")
                        }
                    } else {
                        // Tolerate the following to be missing when determining the status code:
                        // - Pub, as it can be bootstrapped as part of the Flutter SDK,
                        // - Yarn 2+, as it is provided with the code that uses it,
                        // - scanners, as scanning is basically optional and one scanner would be enough.
                        if (category != "Scanner" && tool.javaClass.simpleName != "Pub"
                            && tool.javaClass.simpleName != "Yarn2"
                        ) {
                            statusCode = statusCode or 4
                        }

                        Pair(DANGER_PREFIX, "Tool not found.")
                    }

                    append(prefix)
                    append("${tool.javaClass.simpleName}: Requires '${tool.command()}' in ")

                    if (tool.getVersionRequirement().isSatisfiedByAny) {
                        append("no specific version. ")
                    } else {
                        append("version ${tool.getVersionRequirement()}. ")
                    }

                    append(suffix)
                }

                echo(message)
            }

            echo()
        }

        echo("Prefix legend:")
        echo("${DANGER_PREFIX}The tool was not found in the PATH environment.")
        echo("${WARNING_PREFIX}The tool was found in the PATH environment, but not in the required version.")
        echo("${SUCCESS_PREFIX}The tool was found in the PATH environment in the required version.")

        echo()
        if (scanCodeLicenseTextDir != null) {
            echo(Theme.Default.info("ScanCode license texts found in '$scanCodeLicenseTextDir'."))
        } else {
            echo(Theme.Default.warning("ScanCode license texts not found."))
        }

        if (statusCode != 0) {
            echo()
            echo(Theme.Default.warning("Not all tools were found in their required versions."))
            throw ProgramResult(statusCode)
        }
    }
}
