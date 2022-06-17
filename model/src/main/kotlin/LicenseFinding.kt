/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonInclude

import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

/**
 * A class representing a license finding. License findings can point to single licenses or to complex
 * [SpdxExpression]s, depending on the capabilities of the used license scanner. [LicenseFindingCuration]s can also be
 * used to create findings with complex expressions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LicenseFinding(
    /**
     * The found SPDX expression.
     */
    val license: SpdxExpression,

    /**
     * The text location where the license was found.
     */
    val location: TextLocation,

    /**
     * The score of a license finding. Its exact meaning is scanner-specific, but it should give some hint at how much
     * the finding can be relied on / how confident the scanner is to be right. In most cases this is a percentage where
     * 100.0 means that the scanner is 100% confident that the finding is correct.
     */
    val score: Float? = null
) : Comparable<LicenseFinding> {
    companion object {
        private val COMPARATOR = compareBy<LicenseFinding>({ it.license.toString() }, { it.location })

        /**
         * Create a [LicenseFinding] with [detectedLicenseMapping]s applied.
         */
        fun createAndMap(
            license: String,
            location: TextLocation,
            score: Float? = null,
            detectedLicenseMapping: Map<String, String>
        ): LicenseFinding = LicenseFinding(
            license = if (detectedLicenseMapping.isEmpty()) {
                license
            } else {
                license.applyDetectedLicenseMapping(detectedLicenseMapping)
            }.toSpdx(),
            location = location,
            score = score
        )
    }

    constructor(license: String, location: TextLocation, score: Float? = null) : this(license.toSpdx(), location, score)

    override fun compareTo(other: LicenseFinding) = COMPARATOR.compare(this, other)
}

/**
 * Apply [detectedLicenseMapping] from the [org.ossreviewtoolkit.model.config.ScannerConfiguration] to any license
 * String.
 */
private fun String.applyDetectedLicenseMapping(detectedLicenseMapping: Map<String, String>): String {
    var result = this
    detectedLicenseMapping.forEach { (from, to) ->
        val regex = """(^| |\()(${Regex.escape(from)})($| |\))""".toRegex()

        result = regex.replace(result) {
            "${it.groupValues[1]}${to}${it.groupValues[3]}"
        }
    }

    return result
}
