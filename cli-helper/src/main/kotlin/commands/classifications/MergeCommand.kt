/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.commands.classifications

import com.fasterxml.jackson.module.kotlin.readValue

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.check
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.expandTilde

internal class MergeCommand : OrtHelperCommand(
    help = "Merge multiple files with license classifications into one."
) {
    private val licenseClassificationsFiles by argument(
        help = "The license classifications file to merge, in order. Existing classifications will be maintained " +
            "unless they are redefined, in which case they will be overwritten."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .multiple()
        .check("At least two files are required for merging.") { it.size >= 2 }

    private val mergedLicenseClassificationsFile by option(
        "--output", "-o",
        help = "The file to write the merged license classifications to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }

    override fun run() {
        val mergedClassifications = licenseClassificationsFiles.map {
            yamlMapper.readValue<LicenseClassifications>(it)
        }.reduce { existing, other ->
            existing.merge(other)
        }.sort()

        val yaml = mergedClassifications.toYaml()

        mergedLicenseClassificationsFile?.run {
            writeText(yaml)
        } ?: println(yaml)
    }
}
