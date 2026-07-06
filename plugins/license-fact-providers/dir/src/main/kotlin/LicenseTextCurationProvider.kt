/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProviderFactory
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseText
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

/**
 * The name of the ORT license text curations configuration directory.
 */
const val ORT_LICENSE_TEXT_CURATIONS_DIRNAME = "license-text-curations"

/** The configuration for the license text-curations-directory-based license fact provider. */
data class LicenseTextCurationProviderConfig(
    /** The directory that contains the license text curations. */
    val dir: String
)

/**
 * A license fact provider that reads license text curations from the default license texts curations directory. It only
 * returns id-specific license texts.
 */
@OrtPlugin(
    displayName = "Default License Text Curation Provider",
    summary = "Provide project- and / or package-specific license texts from the default directory.",
    factory = LicenseFactProviderFactory::class
)
class DefaultLicenseTextCurationProvider(
    descriptor: PluginDescriptor = DefaultLicenseTextCurationProviderFactory.descriptor
) : LicenseTextCurationProvider(
    descriptor,
    LicenseTextCurationProviderConfig(
        dir = ortConfigDirectory.resolve(ORT_LICENSE_TEXT_CURATIONS_DIRNAME).absolutePath
    )
)

/**
 * A license fact provider that reads license text curations from a local directory. It only returns id-specific license
 * texts.
 */
@OrtPlugin(
    id = "LicenseTextCurationProvider",
    displayName = "License Text Curation Provider",
    summary = "Provide project- and / or package-specific license texts from a custom directory.",
    factory = LicenseFactProviderFactory::class
)
open class LicenseTextCurationProvider(
    override val descriptor: PluginDescriptor = LicenseTextCurationProviderFactory.descriptor,
    config: LicenseTextCurationProviderConfig
) : LicenseFactProvider() {
    private val licenseTextCurationsDir = File(config.dir).also {
        if (!it.isDirectory) {
            logger.warn {
                "The license text curations directory '${it.absolutePath}' does not exist or is not a directory."
            }
        }
    }

    /** A cache with entries for license text curation files to read each file at most once. */
    private val licenseTextCurationsForIdWithoutVersion = mutableMapOf<Identifier, List<LicenseTextCurations>>()

    private fun getCurationsForId(id: Identifier): List<LicenseTextCuration> =
        licenseTextCurationsForIdWithoutVersion.getOrPut(id.copy(version = "")) {
            val relativeCurationFilePath = "${id.toPath(emptyValue = "_").substringBeforeLast("/")}.yml"
            val curationFile = licenseTextCurationsDir.resolve(relativeCurationFilePath).takeIf { it.isFile }

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
        }.filter { it.isApplicable(id) }.flatMap { it.curations }

    override fun hasLicenseText(licenseOrExceptionId: String) = false

    override fun getLicenseText(licenseOrExceptionId: String) = null

    override fun hasLicenseTextsForId(singleLicenseExpression: String, id: Identifier): Boolean =
        getCurationsForId(id).any { curation -> curation.licenseId.toString() == singleLicenseExpression }

    override fun getLicenseTextsForId(singleLicenseExpression: String, id: Identifier): Set<LicenseText> =
        getCurationsForId(id).mapNotNullTo(mutableSetOf()) { curation ->
            curation.licenseText.takeIf {
                curation.licenseId.toString() == singleLicenseExpression
            }?.let { LicenseText(it) }
        }
}
