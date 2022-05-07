/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.utils

import java.io.File
import java.io.IOException

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.VcsMatcher
import org.ossreviewtoolkit.model.readValue

/**
 * A provider for [PackageConfiguration]s providing exactly the packages of the given list.
 * Throws an exception if there is more than one configuration per [Identifier] and [Provenance].
 */
open class SimplePackageConfigurationProvider(
    configurations: Collection<PackageConfiguration>
) : PackageConfigurationProvider {
    private val configurationsById: Map<Identifier, List<PackageConfiguration>>

    init {
        configurations.checkAtMostOneConfigurationPerIdAndProvenance()

        configurationsById = configurations.groupByTo(HashMap()) { it.id }
    }

    override fun getPackageConfigurations(packageId: Identifier, provenance: Provenance): List<PackageConfiguration> =
        configurationsById[packageId]?.filter { it.matches(packageId, provenance) }.orEmpty()
}

/**
 * A [PackageConfigurationProvider] that provides all [PackageConfiguration]s recursively found in the given directory.
 * All files in the directory with a [known extension][FileFormat.findFilesWithKnownExtensions] must be package
 * configuration files, each containing only a single package configuration. Throws an exception if there is more than
 * one configuration per [Identifier] and [Provenance].
 */
class DirectoryPackageConfigurationProvider(directory: File) :
    SimplePackageConfigurationProvider(readDirectory(directory))

private fun readDirectory(directory: File) =
    FileFormat.findFilesWithKnownExtensions(directory).map { file ->
        runCatching {
            file.readValue<PackageConfiguration>()
        }.getOrElse { e ->
            throw IOException("Error reading package configuration from '${file.absolutePath}'.", e)
        }
    }

/**
 * A [PackageConfigurationProvider] that provides all [PackageConfiguration]s found in the given file. Throws an
 * exception if there is more than one configuration per [Identifier] and [Provenance].
 */
class FilePackageConfigurationProvider(file: File) :
    SimplePackageConfigurationProvider(file.readValue<List<PackageConfiguration>>())

private fun Collection<PackageConfiguration>.checkAtMostOneConfigurationPerIdAndProvenance() {
    data class Key(val id: Identifier, val sourceArtifactUrl: String?, val vcsMatcher: VcsMatcher?)

    fun PackageConfiguration.key() = Key(id, sourceArtifactUrl, vcs)

    val configurationsWithSameMatcher = groupBy { it.key() }.filter { it.value.size > 1 }

    require(configurationsWithSameMatcher.isEmpty()) {
        "There must be at most one package configuration per Id and provenance, but found multiple for:\n" +
                "${configurationsWithSameMatcher.keys.joinToString(prefix = "  ", separator = "\n  ")}."
    }
}
