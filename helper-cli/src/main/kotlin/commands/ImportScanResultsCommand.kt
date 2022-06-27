/*
 * Copyright (C) 2020 HERE Europe B.V.
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
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.readOrtResult
import org.ossreviewtoolkit.scanner.storages.FileBasedStorage
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage

internal class ImportScanResultsCommand : CliktCommand(
    help = "Import all scan results from the given ORT result file to the file based scan results storage directory."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The input ORT file from which repository configuration shall be extracted."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val scanResultsStorageDir by option(
        "--scan-results-storage-dir",
        help = "The scan results storage to import the scan results to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val ortResult = readOrtResult(ortFile)
        val scanResultsStorage = FileBasedStorage(LocalFileStorage(scanResultsStorageDir))
        val ids = ortResult.getProjects().map { it.id } + ortResult.getPackages().map { it.pkg.id }

        ids.forEach { id ->
            ortResult.getScanResultsForId(id).forEach { scanResult ->
                scanResultsStorage.add(id, scanResult)
            }
        }
    }
}
