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

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.CommandWithHelp
import com.here.ort.model.Error
import com.here.ort.model.OrtResult
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.log

import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback

import java.io.File

import javax.script.ScriptEngineManager

@Parameters(commandNames = ["evaluate"], commandDescription = "Evaluate rules on ORT result files.")
object EvaluatorCommand : CommandWithHelp() {
    @Parameter(description = "The ORT result file to use.",
            names = ["--ort-result-file", "-i"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var ortResultFile: File

    @Parameter(description = "The name of a stript file containing rules.",
            names = ["--rules-file", "-r"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var rulesFile: File? = null

    @Parameter(description = "The name of a script resource on the classpath that contains rules.",
            names = ["--rules-resource"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var rulesResource: String? = null

    private val engine = ScriptEngineManager().getEngineByExtension("kts")

    override fun runCommand(jc: JCommander): Int {
        require((rulesFile == null) != (rulesResource == null)) {
            "Either '--rules-file' or '--rules-resource' must be specified."
        }

        // This is required to avoid
        //
        //     WARN: Failed to initialize native filesystem for Windows
        //     java.lang.RuntimeException: Could not find installation home path. Please make sure bin/idea.properties
        //     is present in the installation directory.
        //
        // on Windows for some reason.
        setIdeaIoUseFallback()

        val ortResult = ortResultFile.readValue<OrtResult>()
        engine.put("ortResult", ortResult)

        val preface = """
            import com.here.ort.model.Error
            import com.here.ort.model.OrtResult

            val ortResult = bindings["ortResult"] as OrtResult
            val evalErrors = mutableListOf<Error>()
        """.trimIndent()

        val postface = """
            evalErrors
        """.trimIndent()

        val script = preface + (rulesFile?.readText() ?: javaClass.getResource(rulesResource).readText()) + postface

        @Suppress("UNCHECKED_CAST")
        val evalErrors = engine.eval(script) as List<Error>

        return if (evalErrors.isEmpty()) 0 else 1.also {
            if (log.isErrorEnabled) {
                evalErrors.forEach {
                    log.error(it.toString())
                }
            }
        }
    }
}
