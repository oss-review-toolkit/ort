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

package org.ossreviewtoolkit.helper.commands.packagecuration

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.formatComment
import org.ossreviewtoolkit.helper.common.getSplitCurationFile
import org.ossreviewtoolkit.helper.common.readPackageCurations
import org.ossreviewtoolkit.helper.common.writeAsYaml
import org.ossreviewtoolkit.utils.common.expandTilde

internal class SplitCommand : CliktCommand(
    help = "Split a single curations file into a directory structure using the format '<type>/<namespace>/<name>.yml'."
) {
    private val inputCurationsFile by option(
        "--package-curations-file",
        help = "The input package curations file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputCurationsDir by option(
        "--package-curations-dir",
        help = "The output directory for the curation files."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val curationsByOutputFile = readPackageCurations(inputCurationsFile).groupBy {
            getSplitCurationFile(outputCurationsDir, it.id, inputCurationsFile.extension)
        }

        curationsByOutputFile.forEach { (outputFile, curations) ->
            val curationsToPersist = (readPackageCurations(outputFile) + curations)
                .map { it.formatComment() }
                .distinct()
                .sortedBy { it.id.version }

            curationsToPersist.writeAsYaml(outputFile)
        }
    }
}
