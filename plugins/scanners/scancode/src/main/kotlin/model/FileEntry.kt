/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.scancode.model

import kotlin.collections.flatMap
import kotlin.collections.orEmpty

import kotlinx.serialization.Serializable

import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseWithExceptionExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

/**
 * An interface to be able to treat all versions of file entries the same.
 *
 * Note that the data class constructors of the individual version's implementation of this interface should contain
 * only those properties which are actually present in the data, in the order used in the data, to exactly resemble that
 * version's data model. Other properties required to implement the interface should be added to the body of the data
 * class to clearly separate data model properties from "synthetic" interface properties.
 */
sealed interface FileEntry {
    val path: String
    val type: String
    val detectedLicenseExpressionSpdx: String?
    val licenses: List<LicenseEntry>
    val copyrights: List<CopyrightEntry>
    val scanErrors: List<String>

    // A map of ScanCode license keys associated with their corresponding SPDX license ID.
    val scanCodeKeyToSpdxIdMappings: List<Pair<String, String>>

    @Serializable
    data class Version1(
        override val path: String,
        override val type: String,
        override val licenses: List<LicenseEntry.Version1>,
        override val copyrights: List<CopyrightEntry>,
        override val scanErrors: List<String>
    ) : FileEntry {
        override val detectedLicenseExpressionSpdx = null

        override val scanCodeKeyToSpdxIdMappings by lazy {
            licenses.map { license ->
                license.key to license.getSpdxId()
            }
        }
    }

    @Serializable
    data class Version3(
        override val path: String,
        override val type: String,
        val detectedLicenseExpression: String? = null, // This might be explicitly set to null in JSON.
        override val detectedLicenseExpressionSpdx: String? = null, // This might be explicitly set to null in JSON.
        val licenseDetections: List<LicenseDetection>,
        override val copyrights: List<CopyrightEntry>,
        override val scanErrors: List<String>
    ) : FileEntry {
        companion object {
            private fun SpdxExpression?.licensesAndExceptions(): List<String> =
                this?.decompose().orEmpty().flatMap {
                    when (it) {
                        is SpdxLicenseWithExceptionExpression -> listOf(it.license.toString(), it.exception)
                        else -> listOf(it.toString())
                    }
                }
        }

        override val licenses = licenseDetections.flatMap { it.matches }

        override val scanCodeKeyToSpdxIdMappings by lazy {
            val keyNames = detectedLicenseExpression?.toSpdx().licensesAndExceptions()
            val spdxNames = detectedLicenseExpressionSpdx?.toSpdx().licensesAndExceptions()

            check(keyNames.size == spdxNames.size)

            keyNames.zip(spdxNames)
        }
    }
}

@Serializable
data class LicenseDetection(
    val matches: List<LicenseEntry>
)
