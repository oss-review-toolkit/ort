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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.util.StdConverter

import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * A curation for a license finding. Use it to correct a license finding or to add a license that was not previously
 * detected.
 */
data class LicenseFindingCuration(
    /**
     * A glob to match the file path of a license finding.
     */
    val path: String,

    /**
     * A matcher for the start line of a license finding, matches if the start line matches any of [startLines] or if
     * [startLines] is empty.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonDeserialize(converter = CsvStringToIntListConverter::class)
    @JsonSerialize(converter = IntListToCsvStringConverter::class)
    val startLines: List<Int> = emptyList(),

    /**
     * A matcher for the line count of a license finding, matches if the line count equals [lineCount] or if
     * [lineCount] is null.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val lineCount: Int? = null,

    /**
     * A matcher for the detected license of a license finding, matches if the detected license equals
     * [detectedLicense] or if [detectedLicense] is null.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val detectedLicense: SpdxExpression? = null,

    /**
     * The concluded license as SPDX expression or [org.ossreviewtoolkit.utils.spdx.SpdxConstants.NONE] for no license,
     * see https://spdx.dev/spdx-specification-21-web-version#h.jxpfx0ykyb60.
     */
    val concludedLicense: SpdxExpression,

    /**
     * The reason why the curation was made, out of a predefined choice.
     */
    val reason: LicenseFindingCurationReason,

    /**
     * A comment explaining this [LicenseFindingCuration].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = ""
) {
    init {
        require(startLines.all { it > 0 }) {
            "All start line values must be greater than 0."
        }

        require(lineCount == null || lineCount > 0) {
            "If specified, the line count value must be greater than 0."
        }
    }
}

class IntListToCsvStringConverter : StdConverter<List<Int>, String>() {
    override fun convert(value: List<Int>): String = value.joinToString(separator = ",")
}

class CsvStringToIntListConverter : StdConverter<String, List<Int>>() {
    override fun convert(value: String): List<Int> = value
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.toInt() }
}
