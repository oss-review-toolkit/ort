/*
 * Copyright (C) 2021-2022 Bosch.IO GmbH
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
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.util.regex.Pattern

import org.ossreviewtoolkit.analyzer.AnalyzerResultBuilder
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.DependencyGraphConverter
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.expandTilde

/**
 * A command to convert the dependency representation in an ORT result file between the different supported formats.
 *
 * The input file can contain the serialized form of either a full [OrtResult], an [AnalyzerResult], or a single
 * [ProjectAnalyzerResult]. If one of the supported results is found, it is converted to the specified target format.
 * If this target format is already used, then this command does nothing.
 *
 * This command is also intended to be used for converting files with expected results for ORT tests. Such result
 * files can contain placeholders, which make them invalid (e.g. a placeholder for a numeric value causes serialization
 * to fail). Therefore, a special mode can be enabled, in which well-known placeholders are handled.
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
        },

        /** The classic dependency tree format. */
        TREE {
            override fun convert(result: AnalyzerResult): AnalyzerResult = result.withResolvedScopes()
        };

        /**
         * Convert the given [result] to the target format defined by this literal.
         */
        abstract fun convert(result: AnalyzerResult): AnalyzerResult
    }

    /**
     * A data class to describe the placeholders in result files that needs to be handled by this command.
     */
    private data class PlaceholderInfo(
        /** The name of the property whose value is a placeholder. */
        val property: String,

        /** The name of the placeholder. */
        val placeholder: String,

        /** The value to insert for the placeholder to make the input file valid YAML. */
        val replacement: String
    )

    /**
     * A list of [PlaceholderInfo] objects that are handled during processing. This is a subset of the placeholders
     * used by expected test results in functional tests. It contains those placeholders that prevent the
     * deserialization of a result file, as their name cannot be assigned to the property they are associated with.
     * (This is typically the case for numeric properties that cannot have string values; but other data types
     * requiring a special semantic of string values may be affected as well.) The placeholders listed here are
     * replaced by unproblematic values before loading the result file and later restored again.
     */
    private val typedPlaceholders = listOf(
        PlaceholderInfo("processors", "REPLACE_PROCESSORS", "11"),
        PlaceholderInfo("max_memory", "REPLACE_MAX_MEMORY", "1234")
    )

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

    private val handlePlaceholders by option(
        "--placeholders", "-p",
        help = "Handles special placeholders in the input file that are used by ORT functional tests."
    ).flag("--no-placeholders", "-P")

    override fun run() {
        val converters = sequenceOf(::convertOrtResult, ::convertAnalyzerResult, ::convertProjectAnalyzerResult)
        converters.firstNotNullOfOrNull { it() }?.let { writeResult(it) }
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
            block(readInput())
        }.getOrNull()

    /**
     * Attempt to deserialize the input file to the result type [T]. Handle placeholders if enabled.
     */
    private inline fun <reified T : Any> readInput(): T =
        if (handlePlaceholders) {
            val resultText = replacePlaceholders(ortFile.readText(), inputPlaceholderReplacements())
            yamlMapper.readValue(resultText, T::class.java)
        } else {
            ortFile.readValue()
        }

    /**
     * Convert the given [analyzerResult] to the selected target format if it is present.
     */
    private fun convertToTarget(analyzerResult: AnalyzerResult?): AnalyzerResult? =
        analyzerResult?.let(targetFormat::convert)

    /**
     * Write the result of the conversion to the configured output file. If no output file was specified, overwrite the
     * input file. If necessary, restore placeholders.
     */
    private inline fun <reified T : Any> writeResult(result: T) {
        val targetFile = outputFile ?: ortFile

        println("Writing converted result to $targetFile.")

        if (handlePlaceholders) {
            val yaml = yamlMapper.writeValueAsString(result)
            val text = replacePlaceholders(yaml, outputPlaceholderReplacements())
            targetFile.writeText(text)
        } else {
            targetFile.writeValue(result)
        }
    }

    /**
     * Replace placeholders in the given [text] based on the provided [replacement] information.
     */
    private fun replacePlaceholders(text: String, replacement: Collection<Pair<Regex, String>>): String =
        replacement.fold(text) { s, replace ->
            s.replace(replace.first, replace.second)
        }

    /**
     * Return a collection with replacement information to replace critical placeholders in the input file by values
     * that do not cause problems during deserialization.
     */
    private fun inputPlaceholderReplacements(): List<Pair<Regex, String>> =
        typedPlaceholders.map { replacement(it.property, it.placeholder.toPlaceholder(), it.replacement) }

    /**
     * Return a collection with replacement information to restore the original placeholders when writing the processed
     * result to the output file.
     */
    private fun outputPlaceholderReplacements(): List<Pair<Regex, String>> =
        typedPlaceholders.map { replacement(it.property, it.replacement, it.placeholder.toPlaceholder()) }
}

/**
 * Quote this string, so that it can be used safely in a regular expression.
 */
private fun String.quote(): String = Pattern.quote(this)

/**
 * Convert this string to a placeholder for a property value in a YAML file with expected test results.
 */
private fun String.toPlaceholder(): String = "\"<$this>\""

/**
 * Generate a [Regex] that matches a specific property in a YAML file with the given [name] and [expectedValue].
 */
private fun matchProperty(name: String, expectedValue: String): Regex =
    """(${name.quote()}:\s*)${expectedValue.quote()}""".toRegex()

/**
 * Create a [Pair] with a [Regex] and a replacement string that can be used to replace the [oldValue] of a [property]
 * in a YAML file with a [newValue].
 */
private fun replacement(property: String, oldValue: String, newValue: String): Pair<Regex, String> =
    matchProperty(property, oldValue) to "$1$newValue"
