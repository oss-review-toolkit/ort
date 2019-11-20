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
import com.here.ort.model.OrtResult
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY

import java.io.File

@Parameters(
    commandNames = ["list-packages"],
    commandDescription = "Lists the packages and projects contained in the given ORT result file."
)
class ListPackagesCommand : CommandWithHelp() {
    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The ORT result file to read as input."
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--match-detected-licenses"],
        required = false,
        order = PARAMETER_ORDER_MANDATORY,
        description = "Omit all packages not matching any of the licenses given by this comma separated list."
    )
    private var matchDetectedLicenses: List<String> = emptyList()

    override fun runCommand(jc: JCommander): Int {
        val ortResult = ortResultFile.readValue<OrtResult>()

        val packages = ortResult
            .collectProjectsAndPackages()
            .filter {
                matchDetectedLicenses.isEmpty()
                        || (matchDetectedLicenses - ortResult.getDetectedLicensesForId(it)).isEmpty()
            }
            .sortedBy { it }

        val result = buildString {
            packages.forEach {
                appendln(it.toCoordinates())
            }
        }
        println(result)

        return 0
    }
}
