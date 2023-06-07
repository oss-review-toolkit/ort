/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.commands.compare

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.getCommonParentFile

class CompareCommand : OrtCommand(
    name = "compare",
    help = "Compare two ORT results with various methods."
) {
    private val fileA by argument(help = "The first ORT result file to compare.")
        .convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val fileB by argument(help = "The second ORT result file to compare.")
        .convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val method by option(
        "--method", "-m",
        help = "The method to use when comparing ORT results. Must be one of " +
                "${enumValues<CompareMethod>().map { it.name }}."
    ).enum<CompareMethod>()
        .default(CompareMethod.TEXT_DIFF)

    private val ignoreTime by option(
        "--ignore-time", "-t",
        help = "Ignore time differences."
    ).flag()

    override fun run() {
        if (fileA == fileB) {
            println("The arguments point to the same file.")
            throw ProgramResult(0)
        }

        when (method) {
            CompareMethod.TEXT_DIFF -> {
                val replacements = buildMap {
                    if (ignoreTime) {
                        put("""^(\s+(?:start|end)_time:) "[^"]+"$""", "$1 \"${Instant.EPOCH}\"")
                    }
                }

                val diff = unifiedDiff(fileA, fileB, replacements)
                if (diff.isEmpty()) {
                    println("The ORT results are the same.")
                    throw ProgramResult(0)
                }

                println("The ORT results differ:")

                diff.forEach {
                    println(it)
                }

                throw ProgramResult(1)
            }
        }
    }
}

private enum class CompareMethod {
    TEXT_DIFF
}

private fun unifiedDiff(
    a: File, b: File, replacements: Map<String, String> = emptyMap(), contextSize: Int = 7
): List<String> {
    val replaceRegexes = replacements.mapKeys { (pattern, _) -> Regex(pattern, RegexOption.MULTILINE) }

    val textA = a.readText().let {
        replaceRegexes.entries.fold(it) { text, (from, to) ->
            text.replace(from, to)
        }
    }

    val textB = b.readText().let {
        replaceRegexes.entries.fold(it) { text, (from, to) ->
            text.replace(from, to)
        }
    }

    val linesA = textA.lines()
    val linesB = textB.lines()

    val commonParent = getCommonParentFile(setOf(a, b))
    return UnifiedDiffUtils.generateUnifiedDiff(
        "a/${a.relativeTo(commonParent).invariantSeparatorsPath}",
        "b/${b.relativeTo(commonParent).invariantSeparatorsPath}",
        linesA,
        DiffUtils.diff(linesA, linesB),
        contextSize
    )
}
