/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
    /** The path of the package curation file or directory. */
    val path: String,

    /** A flag to denote whether the path is required to exist. */
    @OrtPluginOption(defaultValue = "false")
    val mustExist: Boolean
)

/**
 * A [PackageCurationProvider] that loads a list of [PackageCuration]s from the [ORT_PACKAGE_CURATIONS_FILENAME] file
 * located in ORT's config directory, which defaults to `~/.ort/config/curations.yml`.
 */
@OrtPlugin(
    displayName = "Default File",
    summary = "Provides package curation data from the default curations file.",
    factory = PackageCurationProviderFactory::class
)
class DefaultFilePackageCurationProvider(descriptor: PluginDescriptor) : FilePackageCurationProvider(
    descriptor,
    FilePackageCurationProviderConfig(
        path = ortConfigDirectory.resolve(ORT_PACKAGE_CURATIONS_FILENAME).absolutePath,
        mustExist = false
    )
)

/**
 * A [PackageCurationProvider] that loads [PackageCuration]s from the [ORT_PACKAGE_CURATIONS_DIRNAME] directory located
 * in ORT's config directory, which defaults to `~/.ort/config/curations`. The directory is searched recursively for
 * curation files in any supported [FileFormat], and each file is expected to contain a list of curations.
 */
@OrtPlugin(
    displayName = "Default Directory",
    summary = "Provides package curation data from the default curations directory.",
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
 * A [PackageCurationProvider] that loads [PackageCuration]s from all given curation paths. A path can either be a file
 * or a directory. In the latter case, the directory is searched recursively for curation files. In any case, all
 * [FileFormat]s are supported, and each file is expected to contain a list of curations.
 */
@OrtPlugin(
    displayName = "File",
    summary = "Provides package curation data from all given paths.",
    factory = PackageCurationProviderFactory::class
)
open class FilePackageCurationProvider(descriptor: PluginDescriptor, vararg paths: File?) :
    SimplePackageCurationProvider(descriptor, readCurationFiles(paths.filterNotNull())) {
    constructor(descriptor: PluginDescriptor, config: FilePackageCurationProviderConfig) : this(
        descriptor,
        File(config.path).takeUnless { !it.exists() && !config.mustExist }
    )

    constructor(vararg paths: File?) : this(FilePackageCurationProviderFactory.descriptor, *paths)
}

private class DuplicatedCurationException(message: String?) : Exception(message)

/**
 * Read a list of [PackageCuration]s from existing [paths], which can either point to files or directories. In
 * the latter case, the directory is searched recursively for deserializable files (according to their
 * extension), which then are assumed to be curation files.
 */
private fun readCurationFiles(paths: Collection<File>): List<PackageCuration> {
    val allCurations = mutableListOf<Pair<PackageCuration, File>>()

    val curationFiles = paths.flatMap {
        require(it.exists()) {
            "The path '$it' does not exist."
        }

        if (it.isDirectory) FileFormat.findFilesWithKnownExtensions(it) else listOf(it)
    }.filterNot { it.length() == 0L }

    curationFiles.forEach { curationFile ->
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
