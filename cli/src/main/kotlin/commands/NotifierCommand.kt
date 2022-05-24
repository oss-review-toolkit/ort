/*
 * Copyright (C) 2021-2022 Bosch.IO GmbH
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
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.cli.GlobalOptions
import org.ossreviewtoolkit.cli.utils.configurationGroup
import org.ossreviewtoolkit.cli.utils.inputGroup
import org.ossreviewtoolkit.cli.utils.readOrtResult
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.notifier.Notifier
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.ORT_NOTIFIER_SCRIPT_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

class NotifierCommand : CliktCommand(name = "notify", help = "Create notifications based on an ORT result.") {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
        .inputGroup()

    private val notificationsFile by option(
        "--notifications-file", "-n",
        help = "The name of a Kotlin script file containing notification rules."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .inputGroup()

    private val resolutionsFile by option(
        "--resolutions-file",
        help = "A file containing issue and rule violation resolutions."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_RESOLUTIONS_FILENAME))
        .configurationGroup()

    private val labels by option(
        "--label", "-l",
        help = "Set a label in the ORT result passed to the notifier script, overwriting any existing label of the " +
                "same name. Can be used multiple times. For example: --label distribution=external"
    ).associate()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    override fun run() {
        val script = notificationsFile?.readText() ?: readDefaultNotificationsFile()

        val ortResult = readOrtResult(ortFile).mergeLabels(labels)

        val config = globalOptionsForSubcommands.config.notifier

        val notifier = Notifier(ortResult, config, DefaultResolutionProvider.create(ortResult, resolutionsFile))

        notifier.run(script)
    }

    private fun readDefaultNotificationsFile(): String {
        val notificationsFile = ortConfigDirectory.resolve(ORT_NOTIFIER_SCRIPT_FILENAME)

        if (!notificationsFile.isFile) {
            throw UsageError(
                "No notifications file option specified and no default notifications file found at " +
                        "'$notificationsFile'."
            )
        }

        return notificationsFile.readText()
    }
}
