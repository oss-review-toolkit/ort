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
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.file

import kotlin.system.exitProcess

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.utils.common.EnvironmentVariableFilter
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.mebibytes
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_DIR_ENV_NAME
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_DATA_DIR_ENV_NAME
import org.ossreviewtoolkit.utils.ort.ORT_NAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.ort.printStackTrace

import org.slf4j.LoggerFactory

/**
 * The entry point for the application with [args] being the list of arguments.
 */
fun main(args: Array<String>) {
    Os.fixupUserHomeProperty()
    OrtMain().main(args)
    exitProcess(0)
}

class OrtMain : CliktCommand(name = ORT_NAME, invokeWithoutSubcommand = true) {
    private companion object : Logging

    private val configFile by option("--config", "-c", help = "The path to a configuration file.")
        .convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .default(ortConfigDirectory.resolve(ORT_CONFIG_FILENAME))

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

    private inner class OrtHelpFormatter : CliktHelpFormatter(requiredOptionMarker = "*", showDefaultValues = true) {
        var headerShownBefore = false

        override fun formatHelp(
            prolog: String,
            epilog: String,
            parameters: List<HelpFormatter.ParameterHelp>,
            programName: String
        ) =
            buildString {
                // The header only needs to be shown for the root command, as for subcommands the header was already
                // shown by the root command's run(). However, only show it if it has not been shown before as part of
                // "--help-all" (note that we cannot safely access the "helpAll" variable here as it might not have been
                // initialized yet.)
                val isRootCommand = currentContext.invokedSubcommand == null
                if (isRootCommand && !headerShownBefore) {
                    appendLine(getOrtHeader(env.ortVersion))
                    headerShownBefore = true
                }

                appendLine(super.formatHelp(prolog, epilog, parameters, programName))
                appendLine()
                appendLine("* denotes required options.")
            }
    }

    init {
        completionOption()

        context {
            expandArgumentFiles = false
            helpFormatter = OrtHelpFormatter()
        }

        subcommands(OrtCommand.ALL.values)

        versionOption(
            version = env.ortVersion,
            names = setOf("--version", "-v"),
            help = "Show the version and exit.",
            message = { it }
        )
    }

    override fun run() {
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        rootLogger.level = logLevel

        logger.debug { "Used command line arguments: ${currentContext.originalArgv}" }

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
                println(it.getFormattedHelp())
            }
        } else {
            println(getOrtHeader(env.ortVersion))
            println("Looking for ORT configuration in the following file:")
            println("\t" + configFile.absolutePath + " (does not exist)".takeIf { !configFile.exists() }.orEmpty())
            println()
        }
    }

    private fun getOrtHeader(version: String): String {
        val variables = mutableListOf(
            "$ORT_CONFIG_DIR_ENV_NAME = $ortConfigDirectory",
            "$ORT_DATA_DIR_ENV_NAME = $ortDataDirectory"
        )

        env.variables.entries.mapTo(variables) { (key, value) -> "$key = $value" }

        val commandName = currentContext.invokedSubcommand?.commandName
        val command = commandName?.let { " '$commandName'" }.orEmpty()
        val user = System.getProperty("user.name").takeUnless { it == "?" } ?: Os.userHomeDirectory.name

        val header = mutableListOf<String>()
        val maxMemInMib = env.maxMemory / 1.mebibytes

        """
             ______________________________
            /        \_______   \__    ___/ The OSS Review Toolkit, version $version.
            |    |   | |       _/ |    |
            |    |   | |    |   \ |    |    Running$command as '$user' under Java ${env.javaVersion} on ${env.os}
            \________/ |____|___/ |____|    with ${env.processors} CPUs and a maximum of $maxMemInMib MiB of memory.

        """.trimIndent().lines().mapTo(header) {
            it.trimEnd()
        }

        if (variables.isNotEmpty()) {
            header += "Environment variables:"
            header += variables
        }

        return header.joinToString("\n", postfix = "\n")
    }
}
