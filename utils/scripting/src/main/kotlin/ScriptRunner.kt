/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.time.measureTimedValue

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.utils.ort.log

/**
 * A class providing the framework to run Kotlin scripts.
 */
abstract class ScriptRunner {
    /** The scripting host instance. */
    private val scriptingHost = BasicJvmScriptingHost()

    /** The configuration to use to compile the script. */
    protected abstract val compConfig: ScriptCompilationConfiguration

    /** The configuration to use to evaluate the script. */
    protected abstract val evalConfig: ScriptEvaluationConfiguration

    /**
     * Check the syntax of the [script] without evaluating it. Return true if syntax is correct, false otherwise.
     */
    fun checkSyntax(script: String): Boolean {
        val (result, duration) = measureTimedValue {
            runBlocking { scriptingHost.compiler.invoke(script.toScriptSource(), compConfig) }
        }

        log.info { "Compiling the script took $duration." }

        logReports(result.reports)

        return result is ResultWithDiagnostics.Success
    }

    /**
     * Run the given [script], returning a [ResultValue].
     */
    fun runScript(script: String): ResultValue {
        val (result, duration) = measureTimedValue {
            scriptingHost.eval(script.toScriptSource(), compConfig, evalConfig)
        }

        log.info { "Evaluating the script took $duration." }

        logReports(result.reports)

        val value = result.valueOrThrow().returnValue
        if (value is ResultValue.Error) throw value.error

        return value
    }

    private fun logReports(reports: List<ScriptDiagnostic>) =
        reports.forEach { report ->
            when (report.severity) {
                ScriptDiagnostic.Severity.DEBUG -> log.debug(report.message, report.exception)
                ScriptDiagnostic.Severity.INFO -> log.info(report.message, report.exception)
                ScriptDiagnostic.Severity.WARNING -> log.warn(report.message, report.exception)
                ScriptDiagnostic.Severity.ERROR -> log.error(report.message, report.exception)
                ScriptDiagnostic.Severity.FATAL -> log.fatal(report.message, report.exception)
            }
        }
}
