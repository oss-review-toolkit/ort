/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.helper.commands

import com.beust.jcommander.DynamicParameter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import org.ossreviewtoolkit.helper.CommandWithHelp
import org.ossreviewtoolkit.helper.common.IdentifierConverter
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.utils.PARAMETER_ORDER_MANDATORY
import org.ossreviewtoolkit.utils.PARAMETER_ORDER_OPTIONAL

import java.io.File

@Parameters(
    commandNames = ["list-stored-scan-results"],
    commandDescription = "Lists the provenance of all stored scan results for a given package identifier."
)
internal class ListStoredScanResultsCommand : CommandWithHelp() {
    @Parameter(
        description = "The path to the ORT configuration file that configures the scan results storage.",
        names = ["--config"],
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var configFile: File? = null

    @Parameter(
        names = ["--package-id"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        converter = IdentifierConverter::class,
        description = "The target package for which the scan results shall be listed."
    )
    private lateinit var packageId: Identifier

    @DynamicParameter(
        description = "Allows to override values in the configuration file. This option can be used multiple times " +
                "to override multiple values. For example: -Pscanner.postgresStorage.schema=testSchema",
        names = ["-P"]
    )
    private var configArguments = mutableMapOf<String, String>()

    override fun runCommand(jc: JCommander): Int {
        val config = OrtConfiguration.load(configArguments, configFile)
        ScanResultsStorage.configure(config.scanner ?: ScannerConfiguration())

        println("Searching for scan results of '${packageId.toCoordinates()}' in ${ScanResultsStorage.storage.name}.")

        val scanResults = when (val readResult = ScanResultsStorage.storage.read(packageId)) {
            is Success -> readResult.result
            is Failure -> {
                println("Could not read scan results: ${readResult.error}")
                return 2
            }
        }

        println("Found ${scanResults.results.size} scan results:")

        scanResults.results.forEach { result ->
            println("\n${yamlMapper.writeValueAsString(result.provenance)}")
        }

        return 0
    }
}
