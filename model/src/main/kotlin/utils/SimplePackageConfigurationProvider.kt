/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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
class SimplePackageConfigurationProvider(
    configurations: Collection<PackageConfiguration>
) : PackageConfigurationProvider {
    companion object {
        /**
         * A provider without any package configurations.
         */
        @JvmField
        val EMPTY = SimplePackageConfigurationProvider(emptyList())

        /**
         * Return a [SimplePackageConfigurationProvider] which provides all [PackageConfiguration]s found recursively
         * in the given [directory]. All non-hidden files within the given [directory] must be package curation files,
         * each only containing a single package configuration. Throws an exception if there is more than one
         * configuration per [Identifier] and [Provenance].
         */
        fun forDirectory(directory: File): SimplePackageConfigurationProvider {
            val entries = FileFormat.findFilesWithKnownExtensions(directory).mapTo(mutableListOf()) { file ->
                try {
                    file.readValue<PackageConfiguration>()
                } catch (e: IOException) {
                    throw IOException("Error reading package configuration from '${file.absoluteFile}'.", e)
                }
            }

            return SimplePackageConfigurationProvider(entries)
        }

        /**
         * Return a [SimplePackageConfigurationProvider] which provides all [PackageConfiguration]s found in the given
         * file. Throws an exception if there is more than one configuration per [Identifier] and [Provenance].
         */
        fun forFile(file: File): SimplePackageConfigurationProvider {
            val entries = file.readValue<List<PackageConfiguration>>()

            return SimplePackageConfigurationProvider(entries)
        }
    }

    private val configurationsById: Map<Identifier, List<PackageConfiguration>>

    init {
        configurations.checkAtMostOneConfigurationPerIdAndProvenance()

        configurationsById = configurations.groupByTo(HashMap()) { it.id }
    }

    override fun getPackageConfigurations() = configurationsById.values.flatten()

    override fun getPackageConfiguration(packageId: Identifier, provenance: Provenance): PackageConfiguration? =
        configurationsById[packageId]?.filter { it.matches(packageId, provenance) }?.let {
            require(it.size <= 1) { "There must be at most one package configuration per Id and provenance." }
            it.singleOrNull()
        }
}

private fun Collection<PackageConfiguration>.checkAtMostOneConfigurationPerIdAndProvenance() {
    data class Key(val id: Identifier, val sourceArtifactUrl: String?, val vcsMatcher: VcsMatcher?)

    fun PackageConfiguration.key() = Key(id, sourceArtifactUrl, vcs)

    val configurationsWithSameMatcher = groupBy { it.key() }.filter { it.value.size > 1 }

    require(configurationsWithSameMatcher.isEmpty()) {
        "There must be at most one package configuration per Id and provenance, but found multiple for:\n" +
                "${configurationsWithSameMatcher.keys.joinToString(prefix = "  ", separator = "\n  ")}."
    }
}
