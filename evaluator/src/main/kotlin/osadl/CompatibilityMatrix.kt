/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.evaluator.osadl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * An object that represents the OSADL compliance matrix. For details see
 * https://www.osadl.org/Access-to-raw-data.oss-compliance-raw-data-access.0.html.
 */
object CompatibilityMatrix {
    /**
     * A class to represent the information contained in a cell of the matrix, i.e. the result of a lookup.
     */
    data class Info(
        val compatibility: Compatibility,
        val explanation: String
    ) {
        companion object {
            val NOT_AVAILABLE = Info(
                compatibility = Compatibility.UNKNOWN,
                explanation = "This combination of licenses is not covered by the compliance matrix."
            )
        }
    }

    private val matrix by lazy {
        javaClass.getResourceAsStream("/rules/osadl-matrix-with-explanations.json").use {
            Json.Default.decodeFromStream<MatrixLicenses>(it)
        }.licenses.associate { row ->
            // Use names as keys for faster lookup.
            row.name to row.compatibilities.associate { it.name to Info(it.compatibility, it.explanation) }
        }
    }

    /**
     * Return the compatibility information from the matrix by looking up information for the [leadingLicense] (e.g.
     * outbound license) and [subordinateLicense] (e.g. inbound license).
     */
    fun getCompatibilityInfo(leadingLicense: String, subordinateLicense: String): Info =
        matrix[leadingLicense]?.get(subordinateLicense) ?: Info.NOT_AVAILABLE
}
