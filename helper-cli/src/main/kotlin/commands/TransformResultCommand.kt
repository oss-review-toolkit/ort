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
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import com.schibsted.spt.data.jslt.Parser

import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.utils.common.expandTilde

/**
 * A command to transform an ORT result using a JSLT expression.
 *
 * See https://github.com/schibsted/jslt.
 */
class TransformResultCommand : CliktCommand(
    name = "transform", help = "Implements a JSLT transformation on the given ORT result file."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val transformationFile by option(
        "--transformation-file",
        help = "The file defining the JSLT transformation to apply."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputFile by option(
        "--output-file", "-o",
        help = "The file in which to write the result of the transformation."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val node = ortFile.mapper().readTree(ortFile)

        val expression = Parser.compile(transformationFile)

        val result = expression.apply(node)
        outputFile.writeValue(result)

        println("Wrote result to $outputFile.")
    }
}
