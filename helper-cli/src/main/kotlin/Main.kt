/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package com.here.ort.helper

import kotlin.system.exitProcess

import com.beust.jcommander.JCommander

import com.here.ort.CommandWithHelp
import com.here.ort.helper.commands.FormatRepositoryConfigurationCommand
import com.here.ort.helper.commands.GenerateProjectExcludesCommand
import com.here.ort.helper.commands.GenerateRuleViolationResolutionsCommand
import com.here.ort.helper.commands.GenerateScopeExcludesCommand
import com.here.ort.helper.commands.GenerateTimeoutErrorResolutionsCommand
import com.here.ort.helper.commands.SortRepositoryConfigurationCommand

private const val TOOL_NAME = "orth"

/**
 * The main entry point of the application.
 */
object Main : CommandWithHelp() {
    /**
     * The entry point for the application.
     *
     * @param args The list of application arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args))
    }

    /**
     * Run the ORT HELPER CLI with the provided [args] and return the exit code of [CommandWithHelp.run].
     */
    fun run(args: Array<String>): Int {
        val jc = JCommander(this).apply {
            programName = TOOL_NAME

            addCommand(FormatRepositoryConfigurationCommand())
            addCommand(GenerateProjectExcludesCommand())
            addCommand(GenerateScopeExcludesCommand())
            addCommand(GenerateRuleViolationResolutionsCommand())
            addCommand(GenerateTimeoutErrorResolutionsCommand())
            addCommand(SortRepositoryConfigurationCommand())

            parse(*args)
        }

        return run(jc)
    }

    override fun runCommand(jc: JCommander): Int {
        // JCommander already validates the command names.
        val command = jc.commands[jc.parsedCommand]!!
        val commandObject = command.objects.first() as CommandWithHelp

        // Delegate running actions to the specified command.
        return commandObject.run(jc)
    }
}
