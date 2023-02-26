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

package org.ossreviewtoolkit.plugins.packagecurationproviders.file

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.SimplePackageCurationProvider
import org.ossreviewtoolkit.utils.common.getDuplicates
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

class FilePackageCurationProviderConfig(
    /**
     * The path of the package curation file or directory.
     */
    val path: File,

    /**
     * A flag to denote whether the path is required to exist.
     */
    val mustExist: Boolean
)

open class FilePackageCurationProviderFactory : PackageCurationProviderFactory<FilePackageCurationProviderConfig> {
    override val type = "File"

    override fun create(config: FilePackageCurationProviderConfig) =
        FilePackageCurationProvider(config)

    override fun parseConfig(config: Map<String, String>) =
        FilePackageCurationProviderConfig(
            path = File(config.getValue("path")),
            mustExist = config["mustExist"]?.toBooleanStrict() ?: true,
        )
}

class DefaultFilePackageCurationProviderFactory : FilePackageCurationProviderFactory() {
    override val type = "DefaultFile"

    override fun parseConfig(config: Map<String, String>) =
        FilePackageCurationProviderConfig(
            path = ortConfigDirectory.resolve(ORT_PACKAGE_CURATIONS_FILENAME),
            mustExist = false
        )
}

class DefaultDirPackageCurationProviderFactory : FilePackageCurationProviderFactory() {
    override val type = "DefaultDir"

    override fun parseConfig(config: Map<String, String>) =
        FilePackageCurationProviderConfig(
            path = ortConfigDirectory.resolve(ORT_PACKAGE_CURATIONS_DIRNAME),
            mustExist = false
        )
}

/**
 * A [PackageCurationProvider] that loads [PackageCuration]s from all given curation files. Supports all file formats
 * specified in [FileFormat].
 */
class FilePackageCurationProvider(
    vararg paths: File?
) : SimplePackageCurationProvider(readCurationFiles(paths.filterNotNull())) {
    constructor(config: FilePackageCurationProviderConfig) : this(
        config.path.takeUnless { !it.exists() && !config.mustExist }
    )

    companion object : Logging {
        /**
         * Read a list of [PackageCuration]s from existing [paths], which can either point to files or directories. In
         * the latter case, the directory is searched recursively for deserializable files (according to their
         * extension), which then are assumed to be curation files.
         */
        fun readCurationFiles(paths: Collection<File>): List<PackageCuration> {
            val allCurations = mutableListOf<Pair<PackageCuration, File>>()

            val curationFiles = paths.flatMap {
                require(it.exists()) {
                    "The path '$it' does not exist."
                }

                if (it.isDirectory) FileFormat.findFilesWithKnownExtensions(it) else listOf(it)
            }.filterNot { it.length() == 0L }

            curationFiles.map { curationFile ->
                val curations = runCatching {
                    curationFile.readValue<List<PackageCuration>>()
                }.getOrElse {
                    throw IOException("Failed parsing package curation(s) from '${curationFile.absolutePath}'.", it)
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
