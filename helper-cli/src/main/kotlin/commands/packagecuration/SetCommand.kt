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

package org.ossreviewtoolkit.helper.commands.packagecuration

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.helper.utils.writeOrtResult
import org.ossreviewtoolkit.model.ResolvedPackageCurations.Companion.REPOSITORY_CONFIGURATION_PROVIDER_ID
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.SimplePackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.file.FilePackageCurationProvider
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.config.setPackageCurations

internal class SetCommand : OrtHelperCommand(
    help = "(Re-)set all package curations for a given ORT file to the curations specified via package curations " +
        "file and directory. If no curations are given then all curations get removed."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input and to write the output to."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val packageCurationsDir by option(
        "--package-curations-dir",
        help = "A directory containing package curation files."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val packageCurationsFile by option(
        "--package-curations-file",
        help = "A file containing package curations."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    override fun run() {
        val ortResultInput = readOrtResult(ortFile)

        val packageCurationProviders = buildList {
            val hasRepositoryConfigurationPackageCurations = ortResultInput.resolvedConfiguration.packageCurations.any {
                it.provider.id == REPOSITORY_CONFIGURATION_PROVIDER_ID
            }

            if (hasRepositoryConfigurationPackageCurations) {
                val packageCurations = ortResultInput.repository.config.curations.packages
                add(REPOSITORY_CONFIGURATION_PROVIDER_ID to SimplePackageCurationProvider(packageCurations))
            }

            add("SetCommandOption" to FilePackageCurationProvider(packageCurationsFile, packageCurationsDir))
        }

        val ortResultOutput = ortResultInput.setPackageCurations(packageCurationProviders)

        writeOrtResult(ortResultOutput, ortFile)
    }
}
