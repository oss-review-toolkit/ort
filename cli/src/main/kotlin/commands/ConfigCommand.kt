/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.cli.OrtCommand
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.OrtConfigurationWrapper
import org.ossreviewtoolkit.model.config.REFERENCE_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.expandTilde

class ConfigCommand : OrtCommand(
    name = "config",
    help = "Show different ORT configurations"
) {
    private val showDefault by option(
        "--show-default",
        help = "Show the default configuration used when no custom configuration is present."
    ).flag()

    private val showActive by option(
        "--show-active",
        help = "Show the active configuration used, including any custom configuration."
    ).flag()

    private val showReference by option(
        "--show-reference",
        help = "Show the reference configuration. This configuration is never actually used as it just contains " +
                "example entries for all supported configuration options."
    ).flag()

    private val checkSyntax by option(
        "--check-syntax",
        help = "Check the syntax of the given configuration file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val hoconToYaml by option(
        "--hocon-to-yaml",
        help = "Perform a simple conversion of the given HOCON configuration file to YAML and print the result."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val ortConfig by requireObject<OrtConfiguration>()

    private val mapper = YAMLMapper().apply {
        registerKotlinModule()
    }

    private fun OrtConfiguration.renderYaml() =
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(OrtConfigurationWrapper(this)).removePrefix("---\n")

    override fun run() {
        if (showDefault) {
            println("The default configuration is:")
            println()
            println(OrtConfiguration().renderYaml())
        }

        if (showActive) {
            println("The active configuration is:")
            println()
            println(ortConfig.renderYaml())
        }

        if (showReference) {
            println("The reference configuration is:")
            println()
            println(javaClass.getResource("/$REFERENCE_CONFIG_FILENAME").readText())
        }

        checkSyntax?.run {
            runCatching {
                OrtConfiguration.load(file = this)
            }.onSuccess {
                println("The syntax of the configuration file '$this' is valid.")
            }.onFailure {
                println(it.collectMessages())
                throw ProgramResult(2)
            }
        }

        hoconToYaml?.run {
            println(convertHoconToYaml(readText()))
        }
    }
}

private val curlyBraceToColonRegex = Regex("""^(\s*\w+)\s*\{$""")
private val equalSignToColonRegex = Regex("""^(\s*\w+)\s*=\s*""")

private fun convertHoconToYaml(hocon: String): String {
    val yamlLines = mutableListOf<String>()

    val hoconLines = hocon.lines().map { it.trimEnd() }
    val i = hoconLines.iterator()

    while (i.hasNext()) {
        var line = i.next()
        val trimmedLine = line.trimStart()

        if (trimmedLine.startsWith("//")) {
            line = line.replaceFirst("//", "#")
        }

        if (line.isEmpty() || trimmedLine.startsWith("#")) {
            yamlLines += line
            continue
        }

        if (trimmedLine.endsWith("}")) continue

        line = line.replace(curlyBraceToColonRegex, "$1:")
        line = line.replace(equalSignToColonRegex, "$1: ")

        line = line.replace("\"", "'")

        yamlLines += line
    }

    return yamlLines.joinToString("\n")
}
