/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.commands

import ch.frankel.slf4k.*

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.beust.jcommander.Parameters

import com.here.ort.CommandWithHelp
import com.here.ort.model.OrtResult
import com.here.ort.model.config.Resolutions
import com.here.ort.model.readValue
import com.here.ort.reporter.DefaultResolutionProvider
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.log
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.showStackTrace

import java.io.File

@Parameters(
    commandNames = ["report"],
    commandDescription = "Present Analyzer and Scanner results in various formats."
)
object ReporterCommand : CommandWithHelp() {
    private class ReporterConverter : IStringConverter<Reporter> {
        companion object {
            // Map upper-cased reporter names without the "Reporter" suffix names to their instances.
            val REPORTERS = Reporter.ALL.associateBy { it.toString().removeSuffix("Reporter").toUpperCase() }
        }

        override fun convert(name: String): Reporter {
            return REPORTERS[name.toUpperCase()]
                    ?: throw ParameterException("Reporters must be contained in ${REPORTERS.keys}.")
        }
    }

    @Parameter(
        description = "The ORT result file to use. Must contain a scan result.",
        names = ["--ort-file", "-i"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var ortResultFile: File

    @Parameter(
        description = "The output directory to store the generated reports in.",
        names = ["--output-dir", "-o"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(
        description = "The list of report formats that will be generated.",
        names = ["--report-formats", "-f"],
        converter = ReporterConverter::class,
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var reportFormats: List<Reporter>

    @Parameter(
        description = "A JSON or YAML file that contains resolutions.",
        names = ["--resolutions-file"],
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var resolutionsFile: File? = null

    override fun runCommand(jc: JCommander) {
        require(!outputDir.exists()) {
            "The output directory '${outputDir.absolutePath}' must not exist yet."
        }

        outputDir.safeMkdirs()

        val ortResult = ortResultFile.readValue<OrtResult>()

        val resolutionProvider = DefaultResolutionProvider()
        ortResult.repository.config.resolutions?.let { resolutionProvider.add(it) }
        resolutionsFile?.readValue<Resolutions>()?.let { resolutionProvider.add(it) }

        reportFormats.distinct().forEach {
            try {
                it.generateReport(ortResult, resolutionProvider, outputDir)
            } catch (e: Exception) {
                e.showStackTrace()

                log.error { "Could not create '$it' report: ${e.message}" }
            }
        }
    }
}
