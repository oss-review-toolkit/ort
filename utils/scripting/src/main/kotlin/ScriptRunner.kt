/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.scripting

import java.io.File
import java.net.URL

import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.host.UrlScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.time.measureTimedValue

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.utils.ort.runBlocking

/**
 * A class providing the framework to run Kotlin scripts.
 */
abstract class ScriptRunner<T> {
    /** The scripting host instance. */
    private val scriptingHost = BasicJvmScriptingHost()

    /** The configuration to use to compile the script. */
    protected abstract val compConfig: ScriptCompilationConfiguration

    /** The configuration to use to evaluate the script. */
    protected abstract val evalConfig: ScriptEvaluationConfiguration

    /**
     * Check the syntax of the [script] without evaluating it. Return true if syntax is correct, false otherwise.
     */
    fun checkSyntax(script: SourceCode): Boolean {
        val (result, duration) = measureTimedValue {
            runBlocking { scriptingHost.compiler.invoke(script, compConfig) }
        }

        logger.info { "Compiling the script took $duration." }

        logReports(result.reports)

        return result is ResultWithDiagnostics.Success
    }

    fun checkSyntax(script: File) = checkSyntax(FileScriptSource(script))

    fun checkSyntax(script: String) = checkSyntax(StringScriptSource(script))

    fun checkSyntax(script: URL) = checkSyntax(UrlScriptSource(script))

    /**
     * Run the given [script], returning a value of type [T].
     */
    abstract fun runScript(script: SourceCode): T

    fun runScript(script: File) = runScript(FileScriptSource(script))

    fun runScript(script: String) = runScript(StringScriptSource(script))

    fun runScript(script: URL) = runScript(UrlScriptSource(script))

    protected fun run(script: SourceCode): ResultValue {
        val result = scriptingHost.eval(script, compConfig, evalConfig)

        logReports(result.reports)

        val value = result.valueOrThrow().returnValue
        if (value is ResultValue.Error) throw value.error

        return value
    }

    private fun logReports(reports: List<ScriptDiagnostic>) =
        reports.forEach { report ->
            val renderedReport = report.render(
                withSeverity = false,
                withLocation = true,
                withException = false,
                withStackTrace = false
            )

            when (report.severity) {
                ScriptDiagnostic.Severity.DEBUG -> logger.debug(renderedReport, report.exception)
                ScriptDiagnostic.Severity.INFO -> logger.info(renderedReport, report.exception)
                ScriptDiagnostic.Severity.WARNING -> logger.warn(renderedReport, report.exception)
                ScriptDiagnostic.Severity.ERROR -> logger.error(renderedReport, report.exception)
                ScriptDiagnostic.Severity.FATAL -> logger.fatal(renderedReport, report.exception)
            }
        }
}
