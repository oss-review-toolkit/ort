/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.commands.provenancestorage

import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.YesNoPrompt

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.scanner.ScanStorages
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

internal class DeleteCommand : OrtHelperCommand(
    help = "Deletes stored provenance results matching the options."
) {
    private val configFile by option(
        "--config",
        help = "The path to the ORT configuration file that configures the scan storages."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_CONFIG_FILENAME))

    private val configArguments by option(
        "-P",
        help = "Override a key-value pair in the configuration file. For example: " +
            "-P ort.scanner.storages.postgres.connection.schema=testSchema"
    ).associate()

    private val packageId by option(
        "--package-id",
        help = "Coordinates of the package ID to delete."
    ).convert { Identifier(it) }
        .required()

    private val forceYes by option(
        "--yes", "-y",
        help = "Force yes on all prompts."
    ).flag()

    override fun run() {
        val config = OrtConfiguration.load(configArguments, configFile)
        val scanStorages = ScanStorages.createFromConfig(config.scanner)

        val provenances = scanStorages.packageProvenanceStorage.readProvenances(packageId)
        if (provenances.isEmpty()) {
            val pkgCoords = Theme.Default.success(packageId.toCoordinates())
            echo(Theme.Default.info("No stored provenance found for '$pkgCoords'."))
            return
        }

        val count = Theme.Default.warning(provenances.size.toString())

        echo(Theme.Default.danger("About to delete the following $count provenance(s):"))
        provenances.forEach(::echo)

        if (forceYes || YesNoPrompt("Continue?", terminal).ask() == true) {
            scanStorages.packageProvenanceStorage.deleteProvenances(packageId)
        }
    }
}
