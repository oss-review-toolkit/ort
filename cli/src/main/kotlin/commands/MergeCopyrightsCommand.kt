/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.CommandWithHelp
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.createYamlMapper
import com.here.ort.model.readValue
import com.here.ort.utils.CopyrightStatementsProcessor

import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import java.io.File
import java.text.Collator
import java.util.Locale

@Parameters(commandNames = ["merge-copyrights"], commandDescription = "Merge copyright.txt into copyright garbage.")
object MergeCopyrightsCommand : CommandWithHelp() {
    @Parameter(description = "The input file with one copyright statement per line.",
            names = ["--input-copyrights-file", "-i"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var inputCopyrightFile: File

    @Parameter(description = "Copyright Garbage file.",
            names = ["--output-copyright-garbage", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var outputCopyrightGarbageFile: File

    override fun runCommand(jc: JCommander): Int {
        val newGarbage = inputCopyrightFile
                .readLines()
                .filter { it.trim().isNotEmpty() }
                .toSet()
                .filterAutoRemoved()
        val existingGarbage = outputCopyrightGarbageFile.readValue<CopyrightGarbage>().items

        val collator = Collator.getInstance(Locale("en", "US.utf-8", "POSIX"))
        val resultGarbage = CopyrightGarbage((existingGarbage + newGarbage).toSortedSet(collator))

        val tempFile = createTempFile()
        createYamlMapper().writeValue(tempFile, resultGarbage)

        if (outputCopyrightGarbageFile.isFile) {
            outputCopyrightGarbageFile.renameTo(File("${outputCopyrightGarbageFile.absolutePath}.orig"))
        }
        tempFile.renameTo(outputCopyrightGarbageFile.absoluteFile)
        return 0
    }

    private fun Collection<String>.filterAutoRemoved(): List<String> {
        val result = CopyrightStatementsProcessor().process(this)
        val keptRemovedCopyrights: Set<String> = result.processedStatements.values.flatten().toSet() +
                result.unprocessedStatements.toSet()
        val removedCopyrights = toSet() - keptRemovedCopyrights
        return filter {
            !removedCopyrights.contains(it)
        }
    }
}
