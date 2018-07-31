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

package com.here.ort.reporter

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.here.ort.model.OrtResult

import com.here.ort.model.mapper
import com.here.ort.reporter.reporters.*
import com.here.ort.utils.PARAMETER_ORDER_HELP
import com.here.ort.utils.PARAMETER_ORDER_LOGGING
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.log
import com.here.ort.utils.printStackTrace
import com.here.ort.utils.safeMkdirs

import java.io.File

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {
    const val TOOL_NAME = "reporter"

    private enum class ReportFormat(private val reporter: Reporter) : Reporter {
        EXCEL(ExcelReporter()),
        NOTICE(NoticeReporter()),
        STATIC_HTML(StaticHtmlReporter());

        override fun generateReport(ortResult: OrtResult, outputDir: File) =
                reporter.generateReport(ortResult, outputDir)
    }

    @Parameter(description = "The ort result file to use. Must contain a scan record.",
            names = ["--ort-result-file", "-i"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var ortResultFile: File

    @Parameter(description = "The output directory to store the generated reports in.",
            names = ["--output-dir", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(description = "The list of report formats that will be generated.",
            names = ["--report-formats", "-f"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var reportFormats: List<ReportFormat>

    @Parameter(description = "Enable info logging.",
            names = ["--info"],
            order = PARAMETER_ORDER_LOGGING)
    private var info = false

    @Parameter(description = "Enable debug logging and keep any temporary files.",
            names = ["--debug"],
            order = PARAMETER_ORDER_LOGGING)
    private var debug = false

    @Parameter(description = "Print out the stacktrace for all exceptions.",
            names = ["--stacktrace"],
            order = PARAMETER_ORDER_LOGGING)
    private var stacktrace = false

    @Parameter(description = "Display the command line help.",
            names = ["--help", "-h"],
            help = true,
            order = PARAMETER_ORDER_HELP)
    private var help = false

    /**
     * The entry point for the application.
     *
     * @param args The list of application arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val jc = JCommander(this)
        jc.parse(*args)
        jc.programName = TOOL_NAME

        if (info) {
            log.level = ch.qos.logback.classic.Level.INFO
        }

        if (debug) {
            log.level = ch.qos.logback.classic.Level.DEBUG
        }

        if (help) {
            jc.usage()
            exitProcess(1)
        }

        // Make the parameter globally available.
        printStackTrace = stacktrace

        require(!outputDir.exists()) {
            "The output directory '${outputDir.absolutePath}' must not exist yet."
        }

        outputDir.safeMkdirs()

        val ortResult = ortResultFile.let {
            it.mapper().readValue(it, OrtResult::class.java)
        }

        reportFormats.forEach {
            it.generateReport(ortResult, outputDir)
        }
    }
}
