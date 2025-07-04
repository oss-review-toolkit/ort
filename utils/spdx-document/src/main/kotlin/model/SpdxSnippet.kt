/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.spdxdocument.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression.Strictness.ALLOW_LICENSEREF_EXCEPTIONS
import org.ossreviewtoolkit.utils.spdx.isSpdxExpressionOrNotPresent

/**
 * Information about a snippet that has different licenses and / or copyright than the containing file.
 * See https://spdx.github.io/spdx-spec/v2.3/snippet-information/.
 */
@JsonIgnoreProperties("ranges") // TODO: Implement ranges which is broken in the specification examples.
data class SpdxSnippet(
    /**
     * The unique identifies of this [SpdxSnippet] within a SPDX document.
     */
    @JsonProperty("SPDXID")
    val spdxId: String,

    /**
     * The [SpdxAnnotation]s for the file.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val annotations: List<SpdxAnnotation> = emptyList(),

    /**
     * A general comment about the snippet.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = "",

    /**
     * The copyright text containing the copyright holder of the snippet and the copyright dates if present
     * respectively. Ideally this text is extracted from the actual snippet. To represent a not present value
     * [SpdxConstants.NONE] or [SpdxConstants.NOASSERTION] must be used.
     */
    val copyrightText: String = SpdxConstants.NOASSERTION,

    /**
     * Any relevant background references or analysis that went in to arriving at the concluded License for the file.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val licenseComments: String = "",

    /**
     * The concluded license as SPDX expression. To represent a not present value [SpdxConstants.NONE] or
     * [SpdxConstants.NOASSERTION] must be used.
     */
    val licenseConcluded: String,

    /**
     * The license information found in this file. To represent a not present value [SpdxConstants.NONE] or
     * [SpdxConstants.NOASSERTION] must be used.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val licenseInfoInSnippets: List<String> = emptyList(),

    /**
     * A name for the snippet in a human convenient manner.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val name: String = "",

    /**
     * The ranges in the original host file that the snippet information applies to.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val ranges: List<Range> = emptyList(),

    /**
     * The SPDX reference referencing the document within the SpdxDocument containing the snippet.
     */
    val snippetFromFile: String
) {
    data class Range(
        /** The start of the snippet range. */
        val startPointer: Pointer,

        /** The end of the snippet range. */
        val endPointer: Pointer
    ) {
        init {
            validate()
        }

        fun validate(): Range =
            apply {
                require(
                    (startPointer.lineNumber != null && endPointer.lineNumber != null) ||
                        (startPointer.offset != null && endPointer.offset != null)
                ) {
                    "Start and end pointers need to be of the same type (line number or byte offset)."
                }
            }
    }

    data class Pointer(
        /** SPDX ID of the file. */
        val reference: String,

        /** Line number in the (text) file. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val lineNumber: Int? = null,

        /** Byte offset in the (binary) file. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val offset: Int? = null
    ) {
        init {
            validate()
        }

        fun validate(): Pointer =
            apply {
                require((lineNumber == null) xor (offset == null)) {
                    "The '$reference' pointer must either set the 'lineNumber' or the 'offset' property."
                }
            }
    }

    init {
        validate()
    }

    fun validate(): SpdxSnippet =
        apply {
            require(spdxId.startsWith(SpdxConstants.REF_PREFIX)) {
                "The SPDX ID '$spdxId' has to start with '${SpdxConstants.REF_PREFIX}'."
            }

            require(copyrightText.isNotBlank()) {
                "The copyright text must not be blank."
            }

            require(licenseConcluded.isSpdxExpressionOrNotPresent()) {
                "The license concluded must be either an SpdxExpression, 'NONE' or 'NOASSERTION', but was " +
                    "$licenseConcluded."
            }

            // TODO: The check for [licenseInfoInSnippets] can be made more strict, but the SPDX specification is not
            //       exact enough yet to do this safely.
            licenseInfoInSnippets.filterNot {
                it.isSpdxExpressionOrNotPresent(ALLOW_LICENSEREF_EXCEPTIONS)
            }.let { nonSpdxLicenses ->
                require(nonSpdxLicenses.isEmpty()) {
                    "The entries in 'licenseInfoInSnippets' must each be either an SPDX expression, 'NONE' or " +
                        "'NOASSERTION', but found ${nonSpdxLicenses.joinToString { "'$it'" }}."
                }
            }

            require(name.isNotBlank()) {
                "The snippet name for SPDX-ID '$spdxId' must not be blank."
            }

            require(snippetFromFile.isNotBlank()) {
                "The snippet from file must not be blank."
            }
        }
}
