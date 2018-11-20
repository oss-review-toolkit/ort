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

package com.here.ort.evaluator

import ch.frankel.slf4k.*

import com.here.ort.model.Error
import com.here.ort.model.EvaluatorRun
import com.here.ort.model.OrtResult
import com.here.ort.utils.log

import javax.script.Compilable
import javax.script.ScriptEngineManager
import javax.script.ScriptException

import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback

class Evaluator {
    private val engine = ScriptEngineManager().getEngineByExtension("kts")

    private val preface = """
            import com.here.ort.model.Error
            import com.here.ort.model.OrtResult
            import com.here.ort.model.Package

            val ortResult = bindings["ortResult"] as OrtResult
            val evalErrors = mutableListOf<Error>()

        """.trimIndent()

    private val postface = """

            evalErrors
        """.trimIndent()

    init {
        // This is required to avoid
        //
        //     WARN: Failed to initialize native filesystem for Windows
        //     java.lang.RuntimeException: Could not find installation home path. Please make sure bin/idea.properties
        //     is present in the installation directory.
        //
        // on Windows for some reason.
        setIdeaIoUseFallback()
    }

    private fun buildScript(rulesScript: String) = preface + rulesScript + postface

    fun checkSyntax(ortResult: OrtResult, rulesScript: String): Boolean {
        require(engine is Compilable) {
            "The scripting engine does not support compilation."
        }

        engine.put("ortResult", ortResult)

        return try {
            engine.compile(buildScript(rulesScript))
            true
        } catch (e: ScriptException) {
            log.error { e.message ?: "No error message available." }
            false
        }
    }

    fun evaluate(ortResult: OrtResult, rulesScript: String): EvaluatorRun {
        engine.put("ortResult", ortResult)

        @Suppress("UNCHECKED_CAST")
        val errors = engine.eval(buildScript(rulesScript)) as List<Error>

        return EvaluatorRun(errors)
    }
}
