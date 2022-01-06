/*
 * Copyright (C) 2020-2021 HERE Europe B.V.
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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.core.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.ortConfigDirectory

internal class ListStoredScanResultsCommand : CliktCommand(
    help = "Lists the provenance of all stored scan results for a given package identifier."
) {
    private val configFile by option(
        "--config",
        help = "The path to the ORT configuration file that configures the scan results storage."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_CONFIG_FILENAME))

    private val packageId by option(
        "--package-id",
        help = "The target package for which the scan results shall be listed."
    ).convert { Identifier(it) }
        .required()

    private val configArguments by option(
        "-P",
        help = "Override a key-value pair in the configuration file. For example: " +
                "-P ort.scanner.storages.postgres.schema=testSchema"
    ).associate()

    override fun run() {
        val config = OrtConfiguration.load(configArguments, configFile)
        ScanResultsStorage.configure(config.scanner)

        println("Searching for scan results of '${packageId.toCoordinates()}' in ${ScanResultsStorage.storage.name}.")

        val scanResults = ScanResultsStorage.storage.read(packageId).getOrElse {
            log.error { "Could not read scan results: ${it.message}" }
            throw ProgramResult(1)
        }

        println("Found ${scanResults.size} scan results:")

        scanResults.forEach { result ->
            println("\n${yamlMapper.writeValueAsString(result.provenance)}")
        }
    }
}
