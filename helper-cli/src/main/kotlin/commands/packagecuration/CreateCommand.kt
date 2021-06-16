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
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import java.io.IOException

import org.ossreviewtoolkit.helper.common.createBlockYamlMapper
import org.ossreviewtoolkit.helper.common.getSplitCurationFile
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.utils.expandTilde

internal class CreateCommand : CliktCommand(
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

        if (outputFile.exists()) {
            throw UsageError("File ${outputFile.absolutePath} already exists.")
        }

        val mapper = createBlockYamlMapper()

        try {
            val text = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                listOf(
                    // A block comment without any text is not valid in YAML. Therefore add a dummy comment with a line
                    // break to force the YAML mapper to create a block comment.
                    PackageCuration(packageId, PackageCurationData(comment = "Curation comment.\n"))
                )
            )

            outputFile.parentFile.mkdirs()
            outputFile.writeText(text)
        } catch (e: IOException) {
            throw IOException("Failed to create ${outputFile.absoluteFile}.", e)
        }

        println("${outputFile.absoluteFile} created.")
    }
}
