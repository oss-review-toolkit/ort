/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.scanner.ScanStorages
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

internal class ListStoredScanResultsCommand : OrtHelperCommand(
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
            "-P ort.scanner.storages.postgres.connection.schema=testSchema"
    ).associate()

    override fun run() {
        val config = OrtConfiguration.load(configArguments, configFile)
        val scanStorages = ScanStorages.createFromConfig(config.scanner)

        println(
            "Searching for scan results of '${packageId.toCoordinates()}' in ${scanStorages.readers.size} storage(s)."
        )

        val scanResults = runCatching { scanStorages.read(Package.EMPTY.copy(id = packageId)) }.getOrElse {
            logger.error { "Could not read scan results: ${it.message}" }
            throw ProgramResult(1)
        }

        println("Found ${scanResults.size} scan results:")

        scanResults.forEach { result ->
            println("\n${result.provenance.toYaml()}")
        }
    }
}
