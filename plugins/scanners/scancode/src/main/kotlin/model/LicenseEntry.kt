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

import kotlinx.serialization.Serializable

import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.toSpdxId

private const val LICENSE_REF_PREFIX_SCAN_CODE = "${SpdxConstants.LICENSE_REF_PREFIX}scancode-"

/**
 * An interface to be able to treat all versions of license entries the same.
 *
 * Note that the data class constructors of the individual version's implementation of this interface should contain
 * only those properties which are actually present in the data, in the order used in the data, to exactly resemble that
 * version's data model. Other properties required to implement the interface should be added to the body of the data
 * class to clearly separate data model properties from "synthetic" interface properties.
 */
sealed interface LicenseEntry {
    val licenseExpression: String
    val licenseExpressionSpdx: String?
    val fromFile: String?
    val startLine: Int
    val endLine: Int
    val score: Float
    val matchedText: String?

    @Serializable
    data class Version1(
        val key: String,
        override val score: Float,
        val spdxLicenseKey: String? = null, // This might be explicitly set to null in JSON.
        override val startLine: Int,
        override val endLine: Int,
        val matchedRule: LicenseRule,
        override val matchedText: String? = null
    ) : LicenseEntry {
        override val licenseExpression = matchedRule.licenseExpression
        override val licenseExpressionSpdx = null
        override val fromFile = null

        internal fun getSpdxId(): String =
            // Fall back to building an ID based on the ScanCode-specific "key".
            spdxLicenseKey?.toSpdxId(allowPlusSuffix = true)
                ?: "$LICENSE_REF_PREFIX_SCAN_CODE${key.toSpdxId(allowPlusSuffix = true)}"
    }

    @Serializable
    data class Version3(
        override val licenseExpression: String,
        val spdxLicenseExpression: String? = null,
        override val fromFile: String? = null,
        override val startLine: Int,
        override val endLine: Int,
        override val score: Float,
        override val matchedText: String? = null
    ) : LicenseEntry {
        override val licenseExpressionSpdx = spdxLicenseExpression
    }

    @Serializable
    data class Version4(
        override val licenseExpression: String,
        override val licenseExpressionSpdx: String? = null,
        override val fromFile: String? = null,
        override val startLine: Int,
        override val endLine: Int,
        override val score: Float,
        override val matchedText: String? = null
    ) : LicenseEntry
}

@Serializable
data class LicenseRule(
    val licenseExpression: String
)
