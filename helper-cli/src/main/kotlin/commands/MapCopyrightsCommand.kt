/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package com.here.ort.helper.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.helper.CommandWithHelp
import com.here.ort.helper.common.processAllCopyrightStatements
import com.here.ort.model.OrtResult
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.expandTilde

import java.io.File

@Parameters(
    commandNames = ["map-copyrights"],
    commandDescription = "Reads processed copyright statements from the input file, maps them to unprocessed "
            + "copyright statements using the given ORT file, and writes those mapped statements to the given "
            + "output file."
)
internal class MapCopyrightsCommand : CommandWithHelp() {
    @Parameter(
        names = ["--input-copyrights-file", "-i"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The input copyrights text file containing one processed copyright statement per line."
    )
    private lateinit var inputCopyrightGarbageFile: File

    @Parameter(
        names = ["--output-copyrights-file", "-o"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The output copyrights text file."
    )
    private lateinit var outputCopyrightsFile: File

    @Parameter(
        names = ["--ort-result-file"],
        order = PARAMETER_ORDER_MANDATORY,
        description = "The ORT file utilized for mapping the processed to unprocessed statements."
    )
    private lateinit var ortResultFile: File

    override fun runCommand(jc: JCommander): Int {
        val processedCopyrightStatements = inputCopyrightGarbageFile
            .expandTilde()
            .readText()
            .lines()
            .filter { it.isBlank() }

        val unprocessedCopyrightStatements = ortResultFile
            .expandTilde()
            .readValue<OrtResult>()
            .getUnprocessedCopyrightStatements(processedCopyrightStatements)

        outputCopyrightsFile.writeText(unprocessedCopyrightStatements.joinToString(separator = "\n"))
        return 0
    }
}

private fun OrtResult.getUnprocessedCopyrightStatements(processedStatements: Collection<String>): Set<String> {
    val processedToUnprocessed = mutableMapOf<String, MutableSet<String>>()

    processAllCopyrightStatements().forEach {
        it.rawStatements.forEach { unprocessedStatement ->
            processedToUnprocessed
                .getOrPut(it.statement, { mutableSetOf() })
                .add(unprocessedStatement)
        }
    }

    return processedStatements.flatMapTo(mutableSetOf<String>()) {
        if (processedToUnprocessed.containsKey(it)) processedToUnprocessed[it]!! else listOf(it)
    }
}
