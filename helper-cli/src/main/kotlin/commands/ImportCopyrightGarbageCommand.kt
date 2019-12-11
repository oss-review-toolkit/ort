/*
 * Copyright (C) 2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.helper.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.helper.CommandWithHelp
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.readValue
import com.here.ort.model.yamlMapper
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.expandTilde

import java.io.File
import java.text.Collator
import java.util.Locale

@Parameters(
    commandNames = ["import-copyright-garbage"],
    commandDescription = "Import copyright garbage from a plain text file containing one copyright statement per " +
            "line into the given copyright garbage file."
)
internal class ImportCopyrightGarbageCommand : CommandWithHelp() {
    @Parameter(
        names = ["--input-copyright-garbage-file", "-i"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The input copyright garbage text file."
    )
    private lateinit var inputCopyrightGarbageFile: File

    @Parameter(
        names = ["--output-copyright-garbage-file", "-o"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The output copyright garbage YAML file where the input entries are merged into."
    )
    private lateinit var outputCopyrightGarbageFile: File

    override fun runCommand(jc: JCommander): Int {
        val entriesToImport = inputCopyrightGarbageFile
            .expandTilde()
            .readText()
            .lines()
            .filter { it.isBlank() }

        val existingCopyrightGarbage = if (outputCopyrightGarbageFile.isFile) {
            outputCopyrightGarbageFile.expandTilde().readValue<CopyrightGarbage>().items
        } else {
            emptySet<String>()
        }

        val collator = Collator.getInstance(Locale("en", "US.utf-8", "POSIX"))
        CopyrightGarbage((entriesToImport + existingCopyrightGarbage).toSortedSet(collator)).let {
            yamlMapper.writeValue(outputCopyrightGarbageFile, it)
        }

        return 0
    }
}
