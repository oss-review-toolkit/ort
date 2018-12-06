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
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.readValue
import com.here.ort.utils.CopyrightStatementsProcessor
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import java.io.File

@Parameters(commandNames = ["process-copyrights"], commandDescription = "Merge copyright.txt into copyright garbage.")
object ProcessCopyrightsCommand : CommandWithHelp() {
    @Parameter(description = "The input text file containing one copyright statement per line.",
            names = ["--input-copyrights-file", "-i"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var inputCopyrightFile: File

    @Parameter(description = "Processed, pretty printed copy right text file.",
            names = ["--output-copyright-file", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var outputFile: File

    @Parameter(description = "Copyright Garbage file.",
            names = ["--copyright-garbage-file", "-g"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var copyrightGarbageFile: File

    @Parameter(description = "Add debug information.",
            names = ["--debug", "-d"],
            required = false,
            order = PARAMETER_ORDER_MANDATORY)
    private var debug: Boolean = false

    override fun runCommand(jc: JCommander): Int {
        val garbage = copyrightGarbageFile.readValue<CopyrightGarbage>()
        val inputCopyrights = inputCopyrightFile.readLines().filter { !garbage.items.contains(it) }

        val pretty = CopyrightStatementsProcessor().process(inputCopyrights)
        val result = buildString {
            if (debug) {
                val removed = inputCopyrights.toSet() - (pretty.unprocessedStatements.toSet() +
                        pretty.processedStatements.values.flatten().toSet()).toSortedSet()
                if (removed.isNotEmpty()) {
                    appendln("")
                    removed.forEach {
                        appendln("    $it")
                    }
                }
            }
            pretty.unprocessedStatements.forEach {
                appendln(it)
            }
            pretty.processedStatements.forEach {
                appendln(it.key)
                if (debug) {
                    it.value.toSortedSet().forEach {
                        appendln("    $it")
                    }
                }
            }
        }

        outputFile.writeText(result)
        return 0
    }
}
