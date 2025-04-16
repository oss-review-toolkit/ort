/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.helper.utils.replaceConfig
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.utils.common.expandTilde

internal class GenerateTimeoutErrorResolutionsCommand : OrtHelperCommand(
    help = "Generates resolutions for scanner timeout errors. The result is written to the standard output."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The input ORT file containing the scan timeout errors."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "Override the repository configuration contained in the given input ORT file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val resolutionsFile by option(
        "--resolutions-file",
        help = "A file containing issue resolutions to be used in addition to the ones contained in the given ORT file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val omitExcluded by option(
        "--omit-excluded",
        help = "Only generate issue resolutions for non-excluded projects or packages."
    ).flag()

    override fun run() {
        val ortResult = readOrtResult(ortFile).replaceConfig(repositoryConfigurationFile)

        val resolutionProvider = DefaultResolutionProvider.create(ortResult, resolutionsFile)

        val timeoutIssues = ortResult.getScannerIssues(omitExcluded).flatMapTo(mutableSetOf()) { it.value }.filter {
            it.message.startsWith("ERROR: Timeout") && !resolutionProvider.isResolved(it)
        }

        val generatedResolutions = timeoutIssues.mapTo(mutableSetOf()) {
            IssueResolution(
                message = it.message,
                reason = IssueResolutionReason.SCANNER_ISSUE,
                comment = "TODO"
            )
        }.sortedBy { it.message }

        println(generatedResolutions.toYaml())
    }
}
