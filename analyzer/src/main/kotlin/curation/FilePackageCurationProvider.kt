/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import java.io.IOException

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.PackageCurationProvider
import org.ossreviewtoolkit.analyzer.PackageCurationProviderFactory
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.getDuplicates
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

class FilePackageCurationProviderConfig(
    /**
     * The path of the package curation file or directory.
     */
    val path: File
)

open class FilePackageCurationProviderFactory : PackageCurationProviderFactory<FilePackageCurationProviderConfig> {
    override val name = "File"

    override fun create(config: FilePackageCurationProviderConfig) =
        FilePackageCurationProvider.from(config)

    override fun parseConfig(config: Map<String, String>) =
        FilePackageCurationProviderConfig(path = File(config.getValue("path")))
}

class DefaultFilePackageCurationProviderFactory : FilePackageCurationProviderFactory() {
    override val name = "DefaultFile"

    override fun parseConfig(config: Map<String, String>) =
        FilePackageCurationProviderConfig(path = ortConfigDirectory.resolve(ORT_PACKAGE_CURATIONS_FILENAME))
}

class DefaultDirPackageCurationProviderFactory : FilePackageCurationProviderFactory() {
    override val name = "DefaultDir"

    override fun parseConfig(config: Map<String, String>) =
        FilePackageCurationProviderConfig(path = ortConfigDirectory.resolve(ORT_PACKAGE_CURATIONS_DIRNAME))
}

/**
 * A [PackageCurationProvider] that loads [PackageCuration]s from all given curation files. Supports all file formats
 * specified in [FileFormat].
 */
class FilePackageCurationProvider(
    curationFiles: List<File>
) : SimplePackageCurationProvider(readCurationFiles(curationFiles)) {
    constructor(curationFile: File) : this(listOf(curationFile))

    companion object : Logging {
        fun from(config: FilePackageCurationProviderConfig) =
            with(config.path) { from(file = this, dir = this) }

        fun from(file: File? = null, dir: File? = null): FilePackageCurationProvider {
            val curationFiles = mutableListOf<File>()
            file?.takeIf { it.isFile }?.let { curationFiles += it }
            dir?.takeIf { it.isDirectory }?.let { curationFiles += FileFormat.findFilesWithKnownExtensions(it) }

            return FilePackageCurationProvider(curationFiles)
        }

        fun readCurationFiles(curationFiles: Collection<File>): List<PackageCuration> {
            val allCurations = mutableListOf<Pair<PackageCuration, File>>()

            curationFiles.map { curationFile ->
                val curations = runCatching {
                    curationFile.readValue<List<PackageCuration>>()
                }.getOrElse {
                    throw IOException("Failed parsing package curation from '${curationFile.absolutePath}'.", it)
                }

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
