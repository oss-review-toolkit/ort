/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.ortbomfile

import java.io.File

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.PackageCurationData
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.SimplePackageCurationProvider
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

data class OrtBomFileCurationProviderConfig(
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
class DefaultFilePackageCurationProvider(descriptor: PluginDescriptor) : OrtBomFileCurationProvider(
    descriptor,
    OrtBomFileCurationProviderConfig(
        path = ortConfigDirectory.resolve(ORT_PACKAGE_CURATIONS_FILENAME).absolutePath,
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
open class OrtBomFileCurationProvider(descriptor: PluginDescriptor, vararg paths: File?) :
    SimplePackageCurationProvider(descriptor, readCurationFile(paths.filterNotNull())) {
    constructor(descriptor: PluginDescriptor, config: OrtBomFileCurationProviderConfig) : this(
        descriptor,
        File(config.path).takeUnless { (!it.exists() && !config.mustExist) || it.exists() && it.isDirectory }
    )

    constructor(vararg paths: File?) : this(OrtBomFileCurationProviderFactory.descriptor, *paths)

    companion object {
        fun readCurationFile(paths: Collection<File>): List<PackageCuration> {
            val curationFiles = paths.filterNot { file -> file.length() == 0L }
            require(curationFiles.size == 1) { "Only one package file allowed" }

            val curations = mutableListOf<PackageCuration>()
            val parsedCurations = curationFiles[0].readValue<OrtBomFileProjectCurationsDto>()

            parsedCurations.dependencies.forEach { pck ->
                produceCuration(pck)?.let { curations.add(it) }
            }

            return curations
        }

        private fun produceCuration(pck: DependencyDto): PackageCuration? {
            pck.curation?.let {
                return PackageCuration(
                    id = pck.getIdentifier(),
                    data = pck.getCurationData()
                )
            }
            return null
        }

        private fun DependencyDto.getIdentifier(): Identifier {
            return Identifier.EMPTY
        }

        private fun DependencyDto.getCurationData(): PackageCurationData {
            return PackageCurationData()
        }
    }
}

