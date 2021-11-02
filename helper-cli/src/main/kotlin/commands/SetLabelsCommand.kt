/*
 * Copyright (C) 2021 HERE Europe B.V.
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
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.readOrtResult
import org.ossreviewtoolkit.helper.common.writeOrtResult
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.utils.common.expandTilde

internal class SetLabelsCommand : CliktCommand(
    help = "Set the labels in an ORT result file."
) {
    private val inputOrtFile by option(
        "--input-ort-file", "-i",
        help = "The input ORT result file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputOrtFile by option(
        "--output-ort-file", "-o",
        help = "The output ORT result file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val labels by option(
        "--label", "-l",
        help = "Set a label in the ORT result, overwriting any existing label of the same name. Can be used multiple " +
                "times. For example: --label distribution=external"
    ).associate()

    private val removeExistingLabels by option(
        "--remove-existing-labels",
        help = "Remove all existing labels before adding new labels."
    ).flag()

    override fun run() {
        var ortResult = readOrtResult(inputOrtFile)

        if (removeExistingLabels) {
           ortResult = ortResult.copy(labels = emptyMap())
        }

        writeOrtResult(ortResult.mergeLabels(labels), outputOrtFile)
    }
}
