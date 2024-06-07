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
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.mordant.rendering.Theme

import java.io.File
import java.lang.reflect.Modifier
import java.util.EnumSet

import kotlin.reflect.full.companionObjectInstance

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.scanner.CommandLinePathScannerWrapper
import org.ossreviewtoolkit.scanner.ScannerWrapperConfig
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Plugin
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.spdx.scanCodeLicenseTextDir

import org.reflections.Reflections
import org.reflections.scanners.Scanners

import org.semver4j.Semver

private val DANGER_PREFIX = "\t${Theme.Default.danger("-")} "
private val WARNING_PREFIX = "\t${Theme.Default.warning("+")} "
private val SUCCESS_PREFIX = "\t${Theme.Default.success("*")} "

class RequirementsCommand : OrtCommand(
    name = "requirements",
    help = "Check for the command line tools required by ORT."
) {
    private enum class RequirementsType { PLUGINS, COMMANDS }

    private enum class VersionStatus {
        /** The determined version satisfies ORT's requirements. */
        SATISFIED,

        /** The determined version does not satisfy ORT's requirements. */
        UNSATISFIED,

        /** The tool is available but the version could not be determined. */
        UNKNOWN,

        /** The tool is not available at all (and thus the version could not be determined). */
        UNAVAILABLE
    }

    private val list by option(
        "--list", "-l",
        help = "A comma-separated list of requirements to list."
    ).enum<RequirementsType>().split(",").default(RequirementsType.entries)

    private val reflections by lazy { Reflections("org.ossreviewtoolkit", Scanners.SubTypes) }

    override fun run() {
        if (RequirementsType.PLUGINS in list) listPlugins()

        val status = if (RequirementsType.COMMANDS in list) checkToolVersions() else enumSetOf(VersionStatus.SATISFIED)

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

        if (status.singleOrNull() != VersionStatus.SATISFIED) {
            echo()

            val summary = buildString {
                appendLine("Not all tools requirements were satisfied:")

                if (VersionStatus.UNSATISFIED in status) {
                    appendLine("\t! Some tools were not found in their required versions.")
                }

                if (VersionStatus.UNKNOWN in status) {
                    appendLine("\t! For some tools the version could not be determined.")
                }

                if (VersionStatus.UNAVAILABLE in status) appendLine("\t! Some tools were not found at all.")
            }

            echo(Theme.Default.warning(summary))

            throw ProgramResult(2)
        }
    }

    private fun listPlugins() {
        getPluginsByType().toSortedMap().forEach { (name, all) ->
            echo(Theme.Default.info("$name plugins:"))
            echo(all.joinToString("\n", postfix = "\n") { "${SUCCESS_PREFIX}$it" })
        }
    }

    internal fun getPluginsByType(): Map<String, Set<String>> {
        val pluginClasses = reflections.getSubTypesOf(Plugin::class.java)

        val pluginTypes = pluginClasses.mapNotNull { clazz ->
            val companion = clazz.declaredClasses.find { it.name.endsWith("\$Companion") }
            val all = runCatching { companion?.getDeclaredMethod("getALL") }.getOrNull()

            all?.let {
                @Suppress("UNCHECKED_CAST")
                val plugins = all.invoke(clazz.kotlin.companionObjectInstance) as Map<String, *>
                clazz.simpleName to plugins.keys
            }
        }

        return pluginTypes.toMap()
    }

    private fun checkToolVersions(): EnumSet<VersionStatus> {
        // Toggle bits in here to denote the kind of error. Skip the first bit as status code 1 is already used above.
        val overallStatus = enumSetOf<VersionStatus>()

        getToolsByCategory().forEach { (category, tools) ->
            echo(Theme.Default.info("${category}s:"))

            tools.forEach { tool ->
                val (status, info) = getToolInfo(category, tool)
                overallStatus += status
                echo(info)
            }

            echo()
        }

        return overallStatus
    }

    private fun getToolsByCategory(): Map<String, List<CommandLineTool>> {
        val classes = reflections.getSubTypesOf(CommandLineTool::class.java)

        val tools = mutableMapOf<String, MutableList<CommandLineTool>>()

        classes.filterNot {
            Modifier.isAbstract(it.modifiers) || it.isAnonymousClass || it.isLocalClass
        }.sortedBy { it.simpleName }.forEach {
            runCatching {
                fun Class<out Any>.isBundledPlugin(type: String) =
                    packageName.startsWith("org.ossreviewtoolkit.plugins.$type.")

                val kotlinObject = it.kotlin.objectInstance

                var category = "Other tool"
                val instance = when {
                    kotlinObject != null -> {
                        logger.debug { "$it is a Kotlin object." }

                        when {
                            it.isBundledPlugin("packagemanagers") -> category = "PackageManager"
                            it.isBundledPlugin("versioncontrolsystems") -> category = "VersionControlSystem"
                        }

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
                            ScannerWrapperConfig::class.java
                        ).newInstance("", ScannerWrapperConfig.EMPTY)
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
                    tools.getOrPut(category) { mutableListOf() } += instance
                }
            }.onFailure { e ->
                echo(Theme.Default.danger("There was an error instantiating $it: $e."))
                throw ProgramResult(1)
            }
        }

        return tools
    }

    private fun getToolInfo(category: String, tool: CommandLineTool): Pair<VersionStatus, String> {
        val (status, prefix, suffix) = if (tool.isInPath() || File(tool.command()).isFile) {
            runCatching {
                val actualVersion = tool.getVersion()
                runCatching {
                    val isRequiredVersion = tool.getVersionRequirement().let {
                        Semver.coerce(actualVersion)?.satisfies(it) == true
                    }

                    if (isRequiredVersion) {
                        Triple(VersionStatus.SATISFIED, SUCCESS_PREFIX, "Found version $actualVersion.")
                    } else {
                        Triple(VersionStatus.UNSATISFIED, WARNING_PREFIX, "Found version $actualVersion.")
                    }
                }.getOrElse {
                    Triple(VersionStatus.UNSATISFIED, WARNING_PREFIX, "Found version '$actualVersion'.")
                }
            }.getOrElse {
                val status = if (tool.getVersionRequirement().isSatisfiedByAny) {
                    VersionStatus.SATISFIED
                } else {
                    VersionStatus.UNKNOWN
                }

                Triple(status, WARNING_PREFIX, "Could not determine the version.")
            }
        } else {
            // Tolerate the following to be missing when determining the status code:
            // - Pub, as it can be bootstrapped as part of the Flutter SDK,
            // - Yarn 2+, as it is provided with the code that uses it,
            // - scanners, as scanning is basically optional and one scanner would be enough.
            val status = if (tool.javaClass.simpleName in listOf("Pub", "Yarn2") || category == "Scanner") {
                VersionStatus.SATISFIED
            } else {
                VersionStatus.UNAVAILABLE
            }

            Triple(status, DANGER_PREFIX, "Tool not found.")
        }

        return status to buildString {
            append(prefix)
            append("${tool.displayName()}: Requires '${tool.command()}' in ")

            if (tool.getVersionRequirement().isSatisfiedByAny) {
                append("no specific version. ")
            } else {
                append("version ${tool.getVersionRequirement()}. ")
            }

            append(suffix)
        }
    }
}
