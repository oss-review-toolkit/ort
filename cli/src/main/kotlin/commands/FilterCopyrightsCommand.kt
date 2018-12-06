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

@Parameters(commandNames = ["filter-copyrights"], commandDescription = "Filter copyrights.")
object FilterCopyrightsCommand : CommandWithHelp() {
    @Parameter(description = "The input file with one copyright statement per line.",
            names = ["--input-copyrights", "-i"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var copyrightStatementsFile: File

    @Parameter(description = "Copyright Garbage file.",
            names = ["--copyright-garbage-file", "-g"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var copyrightGarbageFile: File

    @Parameter(description = "Outputfile.",
            names = ["--output-file", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var outputFile: File

    override fun runCommand(jc: JCommander): Int {
        val copyrightStatements = copyrightStatementsFile
                .readLines()
                .filterGarbage(copyrightGarbageFile)
                .filterAutoRemoved()
                .toSortedSet()

        outputFile.writeText(copyrightStatements.joinToString (separator = "\n"))
        return 0
    }

    private fun Collection<String>.filterAutoRemoved(): List<String> {
        val result = CopyrightStatementsProcessor().process(this)
        val keptRemovedCopyrights: Set<String> = result.processedStatements.values.flatten().toSet() +
                result.unprocessedStatements.toSet()
        val removedCopyrights = toSet() - keptRemovedCopyrights
        return filter {
            !removedCopyrights.contains(it)
        }
    }

    private fun Collection<String>.filterGarbage(copyrightGarbageFile: File): List<String> {
        val copyrightGarbage = copyrightGarbageFile.readValue<CopyrightGarbage>()
        return this.filter {
            !copyrightGarbage.items.contains(it)
        }
    }
}
