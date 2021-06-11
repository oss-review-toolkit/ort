/*
 * Copyright (C) 2021 Bosch.IO GmbH
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
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

import com.typesafe.config.ConfigRenderOptions

import io.github.config4k.toConfig

import org.ossreviewtoolkit.cli.GlobalOptions
import org.ossreviewtoolkit.model.config.OrtConfiguration

class ConfigCommand : CliktCommand(name = "config", help = "Show different ORT configurations") {
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

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()
    private val renderOptions = ConfigRenderOptions.defaults().setJson(false).setOriginComments(false)

    private fun OrtConfiguration.renderHocon() = toConfig("ort").root().render(renderOptions)

    override fun run() {
        if (showDefault) {
            println("The default configuration is:")
            println()
            println(OrtConfiguration().renderHocon())
        }

        if (showActive) {
            println("The active configuration is:")
            println()
            println(globalOptionsForSubcommands.config.renderHocon())
        }

        if (showReference) {
            println("The reference configuration is:")
            println()
            println(javaClass.getResource("/reference.conf").readText())
        }
    }
}
