/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import java.text.Collator
import java.util.Locale

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.expandTilde

internal class ImportCopyrightGarbageCommand : OrtHelperCommand(
    help = "Import copyright garbage from a plain text file containing one copyright statement per line into the " +
        "given copyright garbage file."
) {
    private val inputCopyrightGarbageFile by option(
        "--input-copyright-garbage-file", "-i",
        help = "The input copyright garbage text file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputCopyrightGarbageFile by option(
        "--output-copyright-garbage-file", "-o",
        help = "The output copyright garbage YAML file where the input entries are merged into."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val entriesToImport = inputCopyrightGarbageFile.readLines().filterNot { it.isBlank() }

        val existingCopyrightGarbage = if (outputCopyrightGarbageFile.isFile) {
            outputCopyrightGarbageFile.readValue<CopyrightGarbage>().items
        } else {
            emptySet()
        }

        val locale = Locale.Builder().setLanguage("en").setRegion("US").setVariant("POSIX").build()
        val collator = Collator.getInstance(locale)
        CopyrightGarbage((entriesToImport + existingCopyrightGarbage).toSortedSet(collator)).let {
            createYamlMapper().writeValue(outputCopyrightGarbageFile, it)
        }
    }
}

private fun createYamlMapper(): ObjectMapper = yamlMapper.copy().disable(YAMLGenerator.Feature.SPLIT_LINES)
