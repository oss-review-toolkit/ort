/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.here.ort.CommandWithHelp
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
    commandDescription = "Generates resolutions for scanner timeout errors."
)
internal class GenerateTimeoutErrorResolutionsCommand : CommandWithHelp() {
    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--repository-configuration-file"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var repositoryConfigurationFile: File? = null

    @Parameter(
        names = ["--resolutions-file"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var resolutionsFile: File? = null

    @Parameter(
        names = ["--omit-excluded"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
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

            ortResult.repository.config.resolutions?.let {
                resolutions = resolutions.merge(it)
            }

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
