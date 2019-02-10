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

package com.here.ort.utils

import ch.frankel.slf4k.*

import javax.script.Compilable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException

import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback

/**
 * A class providing the framework to run Kotlin scripts.
 */
abstract class ScriptRunner {
    /**
     * The engine to run Kotlin scripts.
     */
    protected val engine: ScriptEngine = ScriptEngineManager().getEngineByExtension("kts")

    /**
     * The text that should get prepended to the main script.
     */
    abstract val preface: String

    /**
     * The text that should get appended to the main script.
     */
    abstract val postface: String

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

    private fun completeScript(script: String) = preface + script + postface

    /**
     * Check the syntax of the [script] without evaluating it. Return true if syntax is correct, false otherwise.
     */
    fun checkSyntax(script: String): Boolean {
        require(engine is Compilable) {
            "The scripting engine does not support compilation."
        }

        return try {
            engine.compile(completeScript(script))
            true
        } catch (e: ScriptException) {
            log.error { e.message ?: "No error message available." }
            false
        }
    }

    /**
     * Run the given [script], returning its last statement.
     */
    open fun run(script: String): Any = engine.eval(completeScript(script))
}
