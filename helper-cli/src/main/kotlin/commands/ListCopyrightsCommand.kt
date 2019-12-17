/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import com.here.ort.helper.common.IdentifierConverter
import com.here.ort.helper.common.getProcessedCopyrightStatements
import com.here.ort.model.Identifier
import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.config.orEmpty
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.expandTilde

import java.io.File

@Parameters(
    commandNames = ["list-copyrights"],
    commandDescription = "Lists the copyright findings."
)
internal class ListCopyrightsCommand : CommandWithHelp() {
    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The ORT result file to read as input."
    )
    private lateinit var ortResultFile: File

    @Parameter(
        description = "A file containing garbage copyright statements entries which are to be ignored.",
        names = ["--copyright-garbage-file"],
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var copyrightGarbageFile: File? = null

    @Parameter(
        names = ["--package-id"],
        order = PARAMETER_ORDER_OPTIONAL,
        converter = IdentifierConverter::class,
        description = "The target package for which the copyrights shall be listed."
    )
    private var packageId: Identifier? = null

    @Parameter(
        names = ["--license-id"],
        order = PARAMETER_ORDER_OPTIONAL,
        description = "The license for which the copyrights shall be listed."
    )
    private var licenseId: String? = null

    @Parameter(
        names = ["--show-raw-statements"],
        order = PARAMETER_ORDER_OPTIONAL,
        description = "Show the raw statements corresponding to each processed statement if these are any different."
    )
    private var showRawStatements: Boolean = false

    override fun runCommand(jc: JCommander): Int {
        val ortResult = ortResultFile.expandTilde().readValue<OrtResult>()
        val copyrightGarbage = copyrightGarbageFile?.expandTilde()?.readValue<CopyrightGarbage>().orEmpty()

        val copyrightStatements = ortResult
            .getProcessedCopyrightStatements(copyrightGarbage = copyrightGarbage.items)
            .filter { packageId == null || it.packageId == packageId }
            .filter { licenseId == null || it.licenseId == licenseId }
            .groupBy({ it.statement }, { it.rawStatements })
            .mapValues { it.value.flatten().toSortedSet() }

        val result = buildString {
            copyrightStatements.forEach { (processedStatement, unprocessedStatements) ->
                appendln(processedStatement)
                if (showRawStatements && unprocessedStatements.size > 1) {
                    unprocessedStatements.forEach {
                        appendln("  $it")
                    }
                }
            }
        }

        println(result)
        return 0
    }
}
