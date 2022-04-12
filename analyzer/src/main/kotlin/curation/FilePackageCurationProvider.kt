/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.curation

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageCurationProvider
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.readValueOrDefault
import org.ossreviewtoolkit.utils.common.getDuplicates
import org.ossreviewtoolkit.utils.core.log

/**
 * A [PackageCurationProvider] that loads [PackageCuration]s from all given curation files. Supports all file formats
 * specified in [FileFormat].
 */
class FilePackageCurationProvider(
    curationFiles: Collection<File>
) : SimplePackageCurationProvider(readCurationFiles(curationFiles)) {
    constructor(curationFile: File) : this(listOf(curationFile))

    companion object {
        fun from(file: File? = null, dir: File? = null): FilePackageCurationProvider {
            val curationFiles = mutableListOf<File>()
            file?.takeIf { it.isFile }?.let { curationFiles += it }
            dir?.let { curationFiles += FileFormat.findFilesWithKnownExtensions(it) }

            return FilePackageCurationProvider(curationFiles)
        }

        fun readCurationFiles(curationFiles: Collection<File>): List<PackageCuration> {
            val allCurations = mutableListOf<Pair<PackageCuration, File>>()

            curationFiles.map { curationFile ->
                val curations = runCatching {
                    curationFile.readValueOrDefault(emptyList<PackageCuration>())
                }.onFailure {
                    log.warn { "Failed parsing package curation from '${curationFile.absoluteFile}'." }
                }.getOrThrow()

                curations.mapTo(allCurations) { it to curationFile }
            }

            val duplicates = allCurations.getDuplicates { it.first }

            if (duplicates.isNotEmpty()) {
                val duplicatesInfo = buildString {
                    duplicates.forEach { (curation, origins) ->
                        appendLine("Curation for '${curation.id.toCoordinates()}' found in all of:")
                        val files = origins.joinToString(separator = "\n") { (_, file) -> file.absolutePath }
                        appendLine(files.prependIndent())
                    }
                }
                throw DuplicatedCurationException("Duplicate curations found:\n${duplicatesInfo.prependIndent()}")
            }

            return allCurations.unzip().first
        }
    }
}

private class DuplicatedCurationException(message: String?) : Exception(message)
