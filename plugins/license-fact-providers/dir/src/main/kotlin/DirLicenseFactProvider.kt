/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.licensefactproviders.dir

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseTextCuration
import org.ossreviewtoolkit.model.LicenseTextCurations
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.idMatches
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProviderFactory
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseText
import org.ossreviewtoolkit.utils.ort.ORT_CUSTOM_LICENSE_TEXTS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

/** The configuration for the directory-based license fact provider. */
data class DirLicenseFactProviderConfig(
    /** The directory that contains the license texts. */
    val licenseTextDir: String
)

/**
 * A license fact provider that reads license information from the default directory. The files must be named after the
 * SPDX-conform license IDs, e.g., 'Apache-2.0' or 'LicenseRef-custom-license'.
 */
@OrtPlugin(
    displayName = "Default Directory License Fact Provider",
    summary = "A license fact provider that reads license information from the default directory.",
    factory = LicenseFactProviderFactory::class
)
class DefaultDirLicenseFactProvider(descriptor: PluginDescriptor = DefaultDirLicenseFactProviderFactory.descriptor) :
    DirLicenseFactProvider(
        descriptor,
        DirLicenseFactProviderConfig(
            licenseTextDir = ortConfigDirectory.resolve(ORT_CUSTOM_LICENSE_TEXTS_DIRNAME).absolutePath
        )
    )

/**
 * A license fact provider that reads license information from a local directory. The files must be named after the
 * SPDX-conform license IDs, e.g., 'Apache-2.0' or 'LicenseRef-custom-license'.
 */
@OrtPlugin(
    id = "Dir",
    displayName = "Directory License Fact Provider",
    summary = "A license fact provider that reads license information from a local directory.",
    factory = LicenseFactProviderFactory::class
)
open class DirLicenseFactProvider(
    override val descriptor: PluginDescriptor = DirLicenseFactProviderFactory.descriptor,
    config: DirLicenseFactProviderConfig
) : LicenseFactProvider() {
    private val licenseTextDir = File(config.licenseTextDir).also {
        if (!it.isDirectory) {
            logger.warn { "The license text directory '${it.absolutePath}' does not exist or is not a directory." }
        }
    }

    /** A cache with entries for license text curation files to read each file at most once. */
    private val licenseTextCurationsForIdWithoutVersion = mutableMapOf<Identifier, List<LicenseTextCurations>>()

    private fun getCurationsForId(id: Identifier): List<LicenseTextCuration> =
        licenseTextCurationsForIdWithoutVersion.getOrPut(id.copy(version = "")) {
            val relativeCurationFilePath = "${id.toPath(emptyValue = "_").substringBeforeLast("/")}.yml"
            val curationFile = licenseTextDir.resolve(relativeCurationFilePath).takeIf { it.isFile }

            curationFile?.let { file ->
                runCatching {
                    file.readValue<List<LicenseTextCurations>>()
                }.getOrElse {
                    throw IOException("Failed reading license text curations from '${file.absolutePath}'.", it)
                }.also {
                    logger.info {
                        "Read ${it.size} license text curations for ${id.toCoordinates()} from ${file.absolutePath}."
                    }
                }
            }.orEmpty()
        }.filter { idMatches(it.id, id) }.flatMap { it.curations }

    private fun getLicenseTextFile(licenseOrExceptionId: String) =
        licenseTextDir.resolve(licenseOrExceptionId).takeIf { it.isFile && it.isNotBlank }

    override fun hasLicenseText(licenseOrExceptionId: String) = getLicenseTextFile(licenseOrExceptionId) != null

    override fun getLicenseText(licenseOrExceptionId: String) =
        getLicenseTextFile(licenseOrExceptionId)?.readText()?.let { LicenseText(it) }

    override fun hasLicenseTextsForId(singleLicenseExpression: String, id: Identifier): Boolean =
        getCurationsForId(id).any { curation -> curation.licenseId.toString() == singleLicenseExpression }

    override fun getLicenseTextsForId(singleLicenseExpression: String, id: Identifier): Set<LicenseText> =
        getCurationsForId(id).mapNotNullTo(mutableSetOf()) { curation ->
            curation.licenseText.takeIf { curation.licenseId.toString() == singleLicenseExpression }?.let { LicenseText(it) }
        }
}

private val File.isNotBlank: Boolean
    get() = useLines { lines -> lines.any { line -> line.any { !it.isWhitespace() } } }
