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

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.readValue

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

import java.time.Instant

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.getCommonParentFile
import org.ossreviewtoolkit.utils.ort.Environment

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
                "${CompareMethod.entries.map { it.name }}."
    ).enum<CompareMethod>()
        .default(CompareMethod.TEXT_DIFF)

    private val ignoreTime by option(
        "--ignore-time", "-t",
        help = "Ignore time differences."
    ).flag()

    private val ignoreEnvironment by option(
        "--ignore-environment", "-e",
        help = "Ignore environment differences."
    ).flag()

    private val ignoreTmpDir by option(
        "--ignore-tmp-dir", "-d",
        help = "Ignore temporary directory differences."
    ).flag()

    override fun run() {
        if (fileA == fileB) {
            println("The arguments point to the same file.")
            throw ProgramResult(0)
        }

        if (fileA.extension != fileB.extension) {
            println("The file arguments need to be of the same type.")
            throw ProgramResult(2)
        }

        when (method) {
            CompareMethod.TEXT_DIFF -> {
                // Reserialize file contents with some data types replaced by invariant strings.
                val deserializer = fileA.mapper()
                val serializer = deserializer.copy().registerModule(
                    SimpleModule().apply {
                        if (ignoreTime) addSerializer(InvariantInstantSerializer())
                        if (ignoreEnvironment) addSerializer(InvariantEnvironmentSerializer())
                    }
                )

                val resultA = deserializer.readValue<OrtResult>(fileA)
                val resultB = deserializer.readValue<OrtResult>(fileB)

                val textA = serializer.writeValueAsString(resultA)
                val textB = serializer.writeValueAsString(resultB)

                // Apply data type independent replacements in the texts.
                val replacements = buildMap {
                    if (ignoreTmpDir) {
                        put("""([/\\][Tt]e?mp[/\\]ort)[/\\-][\w./\\-]+""", "$1")
                    }
                }

                val replacementRegexes = replacements.mapKeys { (pattern, _) -> Regex(pattern, RegexOption.MULTILINE) }

                val linesA = replacementRegexes.replaceIn(textA).lines()
                val linesB = replacementRegexes.replaceIn(textB).lines()

                // Create unified diff output.
                val commonParent = getCommonParentFile(setOf(fileA, fileB))
                val diff = UnifiedDiffUtils.generateUnifiedDiff(
                    "a/${fileA.relativeTo(commonParent).invariantSeparatorsPath}",
                    "b/${fileB.relativeTo(commonParent).invariantSeparatorsPath}",
                    linesA,
                    DiffUtils.diff(linesA, linesB),
                    /* contextSize = */ 7
                )

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

private class InvariantInstantSerializer : StdSerializer<Instant>(Instant::class.java) {
    override fun serialize(value: Instant, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeString("invariant")
    }
}

private class InvariantEnvironmentSerializer : StdSerializer<Environment>(Environment::class.java) {
    override fun serialize(value: Environment, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeString("invariant")
    }
}

private fun Map<Regex, String>.replaceIn(text: String) = entries.fold(text) { currentText, (from, to) ->
    currentText.replace(from, to)
}
