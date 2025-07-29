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

package org.ossreviewtoolkit.cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.rendering.VerticalAlign
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.grid

import kotlin.system.exitProcess

import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.commands.api.OrtCommandFactory
import org.ossreviewtoolkit.utils.common.EnvironmentVariableFilter
import org.ossreviewtoolkit.utils.common.MaskedString
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.mebibytes
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_NAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory
import org.ossreviewtoolkit.utils.ort.printStackTrace

import org.slf4j.LoggerFactory

private const val REQUIRED_OPTION_MARKER = "*"

// A priority value that is higher than any Clikt built-in value for allocating width.
private const val HIGHEST_PRIORITY_FOR_WIDTH = 100

private val ORT_LOGO = """
     ______________________________
    /        \_______   \__    ___/
    |    |   | |       _/ |    |
    |    |   | |    |   \ |    |
    \________/ |____|___/ |____|
""".trimIndent()

/**
 * The entry point for the application with [args] being the list of arguments.
 */
fun main(args: Array<String>) {
    Os.fixupUserHomeProperty()
    OrtMain().main(args)
    exitProcess(0)
}

class OrtMain : CliktCommand(ORT_NAME) {
    private val configFile by option("--config", "-c", help = "The path to a configuration file.")
        .convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .default(ortConfigDirectory / ORT_CONFIG_FILENAME)

    private val logLevel by option(help = "Set the verbosity level of log output.").switch(
        "--error" to Level.ERROR,
        "--warn" to Level.WARN,
        "--info" to Level.INFO,
        "--debug" to Level.DEBUG
    ).default(Level.WARN)

    private val stacktrace by option(help = "Print out the stacktrace for all exceptions.").flag()

    private val configArguments by option(
        "-P",
        help = "Override a key-value pair in the configuration file. For example: " +
            "-P ort.scanner.storages.postgres.connection.schema=testSchema"
    ).associate()

    private val helpAll by option(
        "--help-all",
        help = "Display help for all subcommands."
    ).flag()

    private val env = Environment()

    init {
        completionOption()

        context {
            helpFormatter = { MordantHelpFormatter(context = it, REQUIRED_OPTION_MARKER, showDefaultValues = true) }
        }

        // Pass an empty PluginConfig here as commands are not configurable.
        subcommands(OrtCommandFactory.ALL.map { (_, factory) -> factory.create(PluginConfig.EMPTY) })

        versionOption(
            version = env.ortVersion,
            names = setOf("--version", "-v"),
            help = "Show the version and exit.",
            message = { it }
        )
    }

    override val invokeWithoutSubcommand = true

    override fun helpEpilog(context: Context) = "$REQUIRED_OPTION_MARKER denotes required options."

    override fun run() {
        // This is somewhat dirty: For logging, ORT uses the Log4j API (because of its nice Kotlin API), but Logback as
        // the implementation (for its robustness). The former API does not provide a way to set the root log level,
        // only the Log4j implementation does (via org.apache.logging.log4j.core.config.Configurator). However, the
        // SLF4J API does provide a way to get the root logger and set its level. That is why ORT's CLI additionally
        // depends on the SLF4J API, just to be able to set the root log level below.
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        rootLogger.level = logLevel

        // Make the parameter globally available.
        printStackTrace = stacktrace

        // Make options available to subcommands and apply static configuration.
        val ortConfig = OrtConfiguration.load(args = configArguments, file = configFile)
        currentContext.findOrSetObject { ortConfig }
        LicenseFilePatterns.configure(ortConfig.licenseFilePatterns)

        EnvironmentVariableFilter.reset(
            ortConfig.deniedProcessEnvironmentVariablesSubstrings,
            ortConfig.allowedProcessEnvironmentVariableNames
        )

        if (helpAll) {
            registeredSubcommands().forEach {
                echo(it.getFormattedHelp())
                echo()
            }
        } else {
            echo(getOrtHeader(env.ortVersion))
            echo("Looking for ORT configuration in the following file:")
            echo("\t" + configFile.absolutePath + " (does not exist)".takeIf { !configFile.exists() }.orEmpty())
            echo()
        }
    }

    private fun getOrtHeader(version: String): Widget =
        grid {
            column(0) {
                width = ColumnWidth(width = null, expandWeight = null, priority = HIGHEST_PRIORITY_FOR_WIDTH)
            }

            column(1) { verticalAlign = VerticalAlign.BOTTOM }
            padding { bottom = 1 }

            row {
                cell(ORT_LOGO) { style = TextColors.cyan }

                val commandName = currentContext.invokedSubcommand?.commandName
                val command = commandName?.let { " '${TextColors.cyan(commandName)}'" }.orEmpty()

                val userName = System.getProperty("user.name").takeUnless { it == "?" } ?: Os.userHomeDirectory.name
                val user = userName?.let { " as '${Theme.Default.warning(userName)}'" }.orEmpty()

                val maxMemInMib = env.maxMemory / 1.mebibytes

                cell(
                    """
                        The OSS Review Toolkit, version ${Theme.Default.info(version)},
                        built with JDK ${env.buildJdk}, running under Java ${env.javaVersion}.
                        Executing$command$user on ${env.os}
                        with ${env.processors} CPUs and a maximum of $maxMemInMib MiB of memory.
                    """.trimIndent()
                )
            }

            row {
                val content = mutableListOf("Environment variables:")

                env.variables.mapTo(content) { (key, value) ->
                    val safeValue = value.replaceCredentialsInUri(MaskedString.DEFAULT_MASK)
                    "${Theme.Default.info(key)} = ${Theme.Default.warning(safeValue)}"
                }

                cell(content.joinToString("\n")) { columnSpan = 2 }
            }
        }
}
