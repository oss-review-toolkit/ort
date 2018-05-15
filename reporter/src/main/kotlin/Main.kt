/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException

import com.here.ort.model.ScanRecord
import com.here.ort.model.mapper
import com.here.ort.reporter.reporters.ExcelReporter
import com.here.ort.utils.PARAMETER_ORDER_HELP
import com.here.ort.utils.PARAMETER_ORDER_LOGGING
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.log
import com.here.ort.utils.printStackTrace
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.showStackTrace

import java.io.File

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {
    const val TOOL_NAME = "scanner"

    private enum class ReportFormat {
        EXCEL;

        companion object {
            /**
             * The list of all available report formats.
             */
            @JvmField
            val ALL = ReportFormat.values().asList()
        }
    }

    private class ReportFormatConverter : IStringConverter<ReportFormat> {
        override fun convert(name: String): ReportFormat {
            try {
                return ReportFormat.valueOf(name.toUpperCase())
            } catch (e: IllegalArgumentException) {
                e.showStackTrace()

                throw ParameterException("Report formats must be contained in ${ReportFormat.ALL}.")
            }
        }
    }

    @Parameter(description = "The scan record file to use.",
            names = ["--scan-record-file", "-s"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var scanRecordFile: File

    @Parameter(description = "The output directory to store the generated reports in.",
            names = ["--output-dir", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(description = "The list of report formats that will be generated.",
            names = ["--report-formats", "-f"],
            required = true,
            converter = ReportFormatConverter::class,
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

        val scanRecord = scanRecordFile.let {
            it.mapper().readValue(it, ScanRecord::class.java)
        }

        reportFormats.forEach {
            when (it) {
                ReportFormat.EXCEL -> ExcelReporter().generateReport(scanRecord, outputDir)
            }
        }
    }
}
