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

package org.ossreviewtoolkit.helper.commands.packagecuration

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.analyzer.curation.FilePackageCurationProvider
import org.ossreviewtoolkit.helper.common.readOrtResult
import org.ossreviewtoolkit.helper.common.writeOrtResult
import org.ossreviewtoolkit.utils.common.expandTilde

class SetCommand : CliktCommand(
    help = "(Re-)set all package curations for a given ORT file to the curations specified via package curations " +
            "file and directory. If no curations are given then all curations get removed."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input and to write the output to."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val packageCurationsDir by option(
        "--package-curations-dir",
        help = "A directory containing package curation data."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val packageCurationsFile by option(
        "--package-curations-file",
        help = "A file containing package curation data."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    override fun run() {
        val curations = FilePackageCurationProvider.from(packageCurationsFile, packageCurationsDir).packageCurations

        val ortResult = readOrtResult(ortFile).replacePackageCurations(curations)

        writeOrtResult(ortResult, ortFile)
    }
}
