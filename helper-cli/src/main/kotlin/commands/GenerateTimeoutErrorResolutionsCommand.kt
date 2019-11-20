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

package com.here.ort.helper.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.helper.CommandWithHelp
import com.here.ort.helper.common.getScanIssues
import com.here.ort.model.OrtResult
import com.here.ort.model.config.ErrorResolution
import com.here.ort.model.config.ErrorResolutionReason
import com.here.ort.model.config.Resolutions
import com.here.ort.model.readValue
import com.here.ort.model.yamlMapper
import com.here.ort.reporter.DefaultResolutionProvider
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL

import java.io.File

@Parameters(
    commandNames = ["generate-timeout-error-resolutions"],
    commandDescription = "Generates resolutions for scanner timeout errors. The result is written to the standard " +
            "output."
)
internal class GenerateTimeoutErrorResolutionsCommand : CommandWithHelp() {
    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The input ORT file containing the scan timeout errors."
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--repository-configuration-file"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL,
        description = "Override the repository configuration contained in the given input ORT file."
    )
    private var repositoryConfigurationFile: File? = null

    @Parameter(
        names = ["--resolutions-file"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL,
        description = "A file containing error resolutions to be used in addition to the ones contained in the given " +
                "ORT file."
    )
    private var resolutionsFile: File? = null

    @Parameter(
        names = ["--omit-excluded"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL,
        description = "Only generate error resolutions for non-excluded projects or packages."
    )
    private var omitExcluded: Boolean = false

    override fun runCommand(jc: JCommander): Int {
        var ortResult = ortResultFile.readValue<OrtResult>()
        repositoryConfigurationFile?.let {
            ortResult = ortResult.replaceConfig(it.readValue())
        }

        val resolutionProvider = DefaultResolutionProvider().apply {
            var resolutions = Resolutions()

            resolutionsFile?.let {
                resolutions = resolutions.merge(it.readValue())
            }

            resolutions = resolutions.merge(ortResult.getResolutions())

            add(resolutions)
        }

        val timeoutIssues = ortResult
            .getScanIssues(omitExcluded)
            .filter {
                it.message.startsWith("ERROR: Timeout")
                        && resolutionProvider.getErrorResolutionsFor(it).isEmpty()
            }

        val generatedResolutions = timeoutIssues.map {
            ErrorResolution(
                message = it.message,
                reason = ErrorResolutionReason.SCANNER_ISSUE,
                comment = "TODO"
            )
        }.distinct().sortedBy { it.message }

        println(yamlMapper.writeValueAsString(generatedResolutions))
        return 0
    }
}
