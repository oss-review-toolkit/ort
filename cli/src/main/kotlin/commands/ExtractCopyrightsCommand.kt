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
import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.createYamlMapper
import com.here.ort.model.readValue
import com.here.ort.utils.CopyrightStatementsProcessor

import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import java.io.File
import java.text.Collator
import java.util.Locale

@Parameters(commandNames = ["extract-copyrights"], commandDescription = "Extract copyrights to a plain text file.")
object ExtractCopyrightsCommand : CommandWithHelp() {
    @Parameter(description = "The input file ort result file.",
            names = ["--input-ort-result-file", "-i"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var inputFile: File

    @Parameter(description = "The output text file.",
            names = ["--output-text-file", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    private lateinit var outputFile: File

    override fun runCommand(jc: JCommander): Int {
        val ortResult: OrtResult = inputFile.readValue()
        val result = getCopyrights(ortResult)
        outputFile.writeText(result)

        return 0
    }

    fun getCopyrights(ortResult: OrtResult) =
        buildString {
            ortResult.scanner?.results?.scanResults?.forEach { container ->
                container.results.forEach { scanResult ->
                    scanResult.summary.licenseFindingsMap.values.flatten().forEach { finding ->
                        appendln(finding.statement)
                    }
                }
            }
        }
}
