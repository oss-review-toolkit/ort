/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.cyclonedx

import java.util.SortedSet

import kotlin.collections.filter

import org.cyclonedx.Format
import org.cyclonedx.model.AttachmentText
import org.cyclonedx.model.Hash
import org.cyclonedx.model.License
import org.cyclonedx.model.Property

import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ort.ORT_NAME
import org.ossreviewtoolkit.utils.spdx.SpdxLicense

/**
 * Map each name of a license in the collection to a license object with the appropriate license text.
 */
internal fun Collection<String>.mapNamesToLicenses(origin: String, input: ReporterInput): List<License> =
    map { licenseName ->
        val spdxId = SpdxLicense.forId(licenseName)?.id
        val licenseText = input.licenseFactProvider.getLicenseText(licenseName)

        // Prefer to set the id in case of an SPDX "core" license and only use the name as a fallback, also
        // see https://github.com/CycloneDX/cyclonedx-core-java/issues/8.
        License().apply {
            id = spdxId
            name = licenseName.takeIf { spdxId == null }

            addProperty(Property("$ORT_NAME:origin", origin))

            if (licenseText != null) {
                setLicenseText(
                    AttachmentText().apply {
                        contentType = "plain/text"
                        text = licenseText
                    }
                )
            }
        }
    }

/**
 * Return the license names of all licenses that have any of the given [sources] disregarding the excluded state.
 */
internal fun ResolvedLicenseInfo.getLicenseNames(vararg sources: LicenseSource): SortedSet<String> =
    licenses.filter { license -> sources.any { it in license.sources } }.mapTo(sortedSetOf()) { it.license.toString() }

/**
 * Return the CycloneDX [Format] for the given extension as a [String], or null if there is no match.
 */
internal fun String.toFormat(): Format? = Format.entries.find { this == it.extension }

/**
 * Map an ORT hash object to a CycloneDX hash object.
 */
internal fun org.ossreviewtoolkit.model.Hash.toCycloneDx(): Hash? =
    Hash.Algorithm.entries.find { it.spec == algorithm.toString() }?.let { Hash(it, value) }
