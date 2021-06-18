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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.analyzer.AnalyzerResultBuilder
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.DependencyGraphConverter
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.utils.expandTilde

/**
 * A command to convert the dependency representation in an ORT result file between the different supported formats.
 *
 * The input file can contain the serialized form of either a full [OrtResult], an [AnalyzerResult], or a single
 * [ProjectAnalyzerResult]. If one of the supported results is found, it is converted to the specified target format.
 * If this target format is already used, then this command does nothing.
 */
class SetDependencyRepresentationCommand : CliktCommand(
    help = "Set the dependency representation of an ORT result to a specific target format."
) {
    /**
     * An enumeration class representing the different supported formats to represent dependencies in an ORT result.
     * It is used to define the target format that should be set by this command.
     */
    enum class TargetFormat {
        /** The dependency graph format. */
        GRAPH {
            override fun convert(result: AnalyzerResult): AnalyzerResult =
                DependencyGraphConverter.convert(result)
        };

        /**
         * Convert the given [result] to the target format defined by this literal.
         */
        abstract fun convert(result: AnalyzerResult): AnalyzerResult
    }

    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input. This can be the serialized form of an OrtResult, an " +
                "AnalyzerResult, or a ProjectAnalyzerResult. NOTE: If no output file is specified, this file is " +
                "overwritten."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputFile by option(
        "--output-file", "-o",
        help = "The file in which to write the result of the conversion. If missing, the input file is overwritten."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }

    private val targetFormat by option(
        "--format", "-f",
        help = "The target format for the conversion."
    ).enum<TargetFormat>().default(TargetFormat.GRAPH)

    override fun run() {
        val converters = sequenceOf(::convertOrtResult, ::convertAnalyzerResult, ::convertProjectAnalyzerResult)
        converters.mapNotNull { it() }.firstOrNull()?.let { writeResult(it) }
            ?: throw UsageError("$ortFile does not contain a supported result.")
    }

    /**
     * Convert a full ORT result file.
     */
    private fun convertOrtResult(): Any? =
        convertResult<OrtResult> { result ->
            println("Converting a full ORT result file.")

            convertToTarget(result.analyzer?.result)?.let { analyzerResult ->
                result.copy(
                    analyzer = result.analyzer?.copy(result = analyzerResult)
                )
            }
        }

    /**
     * Convert an [AnalyzerResult] file.
     */
    private fun convertAnalyzerResult(): Any? =
        convertResult<AnalyzerResult> {
            println("Converting an analyzer result file.")

            convertToTarget(it)
        }

    /**
     * Convert a [ProjectAnalyzerResult]. In this case, the result file contains an [AnalyzerResult], as a
     * [ProjectAnalyzerResult] does not contain a dependency graph.
     */
    private fun convertProjectAnalyzerResult(): Any? =
        convertResult<ProjectAnalyzerResult> { result ->
            println("Converting a project analyzer result file.")

            val analyzerResult = AnalyzerResultBuilder().addResult(result).build()
            convertToTarget(analyzerResult)
        }

    /**
     * Convert a result of type [T]. Read this result from the input file and invoke [block] with it. Return *false*
     * if the result file could not be loaded (which probably means that it contains a result of a different type).
     */
    private inline fun <reified T : Any> convertResult(block: (T) -> Any?): Any? =
        runCatching {
            block(ortFile.readValue())
        }.getOrNull()

    /**
     * Convert the given [analyzerResult] to the selected target format if it is present.
     */
    private fun convertToTarget(analyzerResult: AnalyzerResult?): AnalyzerResult? =
        analyzerResult?.let(targetFormat::convert)

    /**
     * Write the result of the conversion to the configured output file. If no output file was specified, overwrite the
     * input file.
     */
    private inline fun <reified T : Any> writeResult(result: T) {
        val targetFile = outputFile ?: ortFile

        println("Writing converted result to $targetFile.")

        targetFile.writeValue(result)
    }
}
