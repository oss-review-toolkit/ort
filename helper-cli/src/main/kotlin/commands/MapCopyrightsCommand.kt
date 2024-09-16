/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.processAllCopyrightStatements
import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.utils.common.expandTilde

internal class MapCopyrightsCommand : OrtHelperCommand(
    help = "Reads processed copyright statements from the input file, maps them to unprocessed copyright statements " +
        "using the given ORT file, and writes those mapped statements to the given output file."
) {
    private val inputCopyrightGarbageFile by option(
        "--input-copyrights-file", "-i",
        help = "The input copyrights text file containing one processed copyright statement per line."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputCopyrightsFile by option(
        "--output-copyrights-file", "-o",
        help = "The output copyrights text file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val ortFile by option(
        "--ort-file",
        help = "The ORT file utilized for mapping the processed to unprocessed statements."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val processedCopyrightStatements = inputCopyrightGarbageFile.readLines().filterNot { it.isBlank() }

        val unprocessedCopyrightStatements = readOrtResult(ortFile)
            .getUnprocessedCopyrightStatements(processedCopyrightStatements)

        outputCopyrightsFile.writeText(unprocessedCopyrightStatements.joinToString(separator = "\n"))
    }
}

private fun OrtResult.getUnprocessedCopyrightStatements(processedStatements: Collection<String>): Set<String> {
    val processedToUnprocessed = mutableMapOf<String, MutableSet<String>>()

    processAllCopyrightStatements().forEach {
        it.rawStatements.forEach { unprocessedStatement ->
            processedToUnprocessed.getOrPut(it.statement) { mutableSetOf() } += unprocessedStatement
        }
    }

    return processedStatements.flatMapTo(mutableSetOf()) { processedToUnprocessed.getOrDefault(it, listOf(it)) }
}
