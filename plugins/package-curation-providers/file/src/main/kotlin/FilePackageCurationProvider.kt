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

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.SimplePackageCurationProvider
import org.ossreviewtoolkit.utils.common.getDuplicates
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

data class FilePackageCurationProviderConfig(
    /**
     * The path of the package curation file or directory.
     */
    val path: String,

    /**
     * A flag to denote whether the path is required to exist.
     */
    @OrtPluginOption(defaultValue = "false")
    val mustExist: Boolean
)

@OrtPlugin(
    displayName = "Default File",
    description = "A package curation provider that loads package curations from the default file.",
    factory = PackageCurationProviderFactory::class
)
class DefaultFilePackageCurationProvider(descriptor: PluginDescriptor) : FilePackageCurationProvider(
    descriptor,
    FilePackageCurationProviderConfig(
        path = ortConfigDirectory.resolve(ORT_PACKAGE_CURATIONS_FILENAME).absolutePath,
        mustExist = false
    )
)

@OrtPlugin(
    displayName = "Default Directory",
    description = "A package curation provider that loads package curations from the default directory.",
    factory = PackageCurationProviderFactory::class
)
class DefaultDirPackageCurationProvider(descriptor: PluginDescriptor) : FilePackageCurationProvider(
    descriptor,
    FilePackageCurationProviderConfig(
        path = ortConfigDirectory.resolve(ORT_PACKAGE_CURATIONS_DIRNAME).absolutePath,
        mustExist = false
    )
)

/**
 * A [PackageCurationProvider] that loads [PackageCuration]s from all given curation files. Supports all file formats
 * specified in [FileFormat].
 */
@OrtPlugin(
    displayName = "File",
    description = "A package curation provider that loads package curations from files.",
    factory = PackageCurationProviderFactory::class
)
open class FilePackageCurationProvider(descriptor: PluginDescriptor, vararg paths: File?) :
    SimplePackageCurationProvider(descriptor, readCurationFiles(paths.filterNotNull())) {
    constructor(descriptor: PluginDescriptor, config: FilePackageCurationProviderConfig) : this(
        descriptor,
        File(config.path).takeUnless { !it.exists() && !config.mustExist }
    )

    constructor(vararg paths: File?) : this(FilePackageCurationProviderFactory.descriptor, *paths)

    companion object {
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
