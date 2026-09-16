/*
 * Copyright (C) 2022 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.commands.api

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject

import java.io.File

import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.plugins.api.Plugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME

/**
 * An interface for [CliktCommand]-based ORT commands that come as [Plugin]s.
 */
abstract class OrtCommand(override val descriptor: PluginDescriptor) : CliktCommand(), Plugin {
    companion object {
        const val OPTION_GROUP_CONFIGURATION = "Configuration Options"
        const val OPTION_GROUP_INPUT = "Input Options"
        const val OPTION_GROUP_OUTPUT = "Output Options"

        /** A status code to indicate success. */
        const val STATUS_CODE_SUCCESS = 0

        /** A status code for fundamental errors that prevented a command from running properly. */
        const val STATUS_CODE_FATAL_ERROR = 1

        /** A status code to indicate that the program did run properly but discovered issues. */
        const val STATUS_CODE_FAILURE = 2
    }

    override fun help(context: Context) = descriptor.summary

    protected val ortConfig by requireObject<OrtConfiguration>()

    /**
     * Checks that the provided [outputFiles] can be used and if so, returns them. Throws a [UsageError] otherwise.
     */
    protected fun checkOutputFiles(outputFiles: Set<File>): Set<File> {
        if (ortConfig.forceOverwrite) return outputFiles

        val existingOutputFiles = outputFiles.filter { it.exists() }
        if (existingOutputFiles.isNotEmpty()) {
            throw UsageError(
                "None of the output files $existingOutputFiles must exist yet. To overwrite output files set the " +
                    "'forceOverwrite' option in '$ORT_CONFIG_FILENAME'."
            )
        }

        return outputFiles
    }

    /**
     * Checks that the provided [outputDirectory] can be used and if so, returns it. Throws a [UsageError] otherwise.
     */
    protected fun checkOutputDirectory(outputDirectory: File): File {
        if (!ortConfig.forceOverwrite) {
            if (outputDirectory.exists() && outputDirectory.walk().singleOrNull() != outputDirectory) {
                throw UsageError(
                    "The output directory '$outputDirectory' must not contain any files yet. To overwrite output " +
                        "files set the 'forceOverwrite' option in '$ORT_CONFIG_FILENAME'."
                )
            }
        }

        return outputDirectory.safeMkdirs()
    }
}
