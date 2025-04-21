/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME

/**
 * An interface for [CliktCommand]-based ORT commands that come as [Plugin]s.
 */
abstract class OrtCommand(override val descriptor: PluginDescriptor) : CliktCommand(), Plugin {
    override fun help(context: Context) = descriptor.description

    protected val ortConfig by requireObject<OrtConfiguration>()

    /**
     * Validates that the provided [outputFiles] can be used. Throws a [UsageError] otherwise.
     */
    protected fun validateOutputFiles(outputFiles: Collection<File>) {
        if (ortConfig.forceOverwrite) return

        val existingOutputFiles = outputFiles.filter { it.exists() }
        if (existingOutputFiles.isNotEmpty()) {
            throw UsageError(
                message = "None of the output files $existingOutputFiles must exist yet. To overwrite output files " +
                    "set the 'forceOverwrite' option in '$ORT_CONFIG_FILENAME'.",
                statusCode = 2
            )
        }
    }
}
