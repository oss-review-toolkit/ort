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

package org.ossreviewtoolkit.plugins.commands.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.Theme

import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.OrtCommandFactory
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.ORT_REFERENCE_CONFIG_FILENAME

@OrtPlugin(
    displayName = "Config",
    description = "Show different ORT configurations.",
    factory = OrtCommandFactory::class
)
class ConfigCommand(descriptor: PluginDescriptor = ConfigCommandFactory.descriptor) : OrtCommand(descriptor) {
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

    private val configWriter = YAMLMapper()
        .registerKotlinModule()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .writerFor(OrtConfiguration::class.java)
        .withRootName("ort")

    private fun OrtConfiguration.renderYaml() = configWriter.writeValueAsString(this).removePrefix("---\n")

    override fun run() {
        if (showDefault) {
            echo("The default configuration is:")
            echo()
            echo(OrtConfiguration().renderYaml())
        }

        if (showActive) {
            echo("The active configuration is:")
            echo()
            echo(ortConfig.renderYaml())
        }

        if (showReference) {
            echo("The reference configuration is:")
            echo()
            val referenceConfigUrl = checkNotNull(javaClass.getResource("/$ORT_REFERENCE_CONFIG_FILENAME"))
            echo(referenceConfigUrl.readText())
        }

        checkSyntax?.run {
            runCatching {
                OrtConfiguration.load(file = this)
            }.onSuccess {
                echo("The syntax of the configuration file '$this' is valid.")
            }.onFailure {
                echo(Theme.Default.danger(it.collectMessages()))
                throw ProgramResult(2)
            }
        }
    }
}
