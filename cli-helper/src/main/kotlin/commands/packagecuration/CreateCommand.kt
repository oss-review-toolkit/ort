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

package org.ossreviewtoolkit.helper.commands.packagecuration

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import java.io.IOException

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.formatComment
import org.ossreviewtoolkit.helper.utils.getSplitCurationFile
import org.ossreviewtoolkit.helper.utils.readPackageCurations
import org.ossreviewtoolkit.helper.utils.writeAsYaml
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.utils.common.expandTilde

internal class CreateCommand : OrtHelperCommand(
    help = "Create a curation file for a package id using a hierarchical directory structure."
) {
    private val packageId by option(
        "--package-id",
        help = "The target package id for which a curation should be generated."
    ).convert { Identifier(it) }
        .required()

    private val outputDir by option(
        "--package-curations-dir",
        help = "The output package curations directory."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = true, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val outputFile = getSplitCurationFile(outputDir, packageId, FileFormat.YAML.fileExtension)

        val curations = readPackageCurations(outputFile).toMutableSet()

        if (curations.any { it.id == packageId }) {
            println("Curation for '${packageId.toCoordinates()}' already exists in '${outputFile.absolutePath}'.")

            return
        }

        curations += PackageCuration(packageId, PackageCurationData(comment = "Curation comment")).formatComment()

        try {
            curations.sortedBy { it.id }.writeAsYaml(outputFile)
        } catch (e: IOException) {
            throw IOException("Failed to create '${outputFile.absoluteFile}'.", e)
        }

        println("Curation created in '${outputFile.absoluteFile}'.")
    }
}
