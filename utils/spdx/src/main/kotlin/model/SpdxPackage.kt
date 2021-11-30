/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.spdx.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.isSpdxExpressionOrNotPresent

/**
 * Describes a software package.
 */
data class SpdxPackage(
    /**
     * A unique identifier for this [SpdxPackage] within a SPDX document.
     */
    @JsonProperty("SPDXID")
    val spdxId: String,

    /**
     * The [SpdxAnnotation]s for the package.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val annotations: List<SpdxAnnotation> = emptyList(),

    /**
     * Acknowledgements for the package that may be required to be communicated in some contexts. This is not meant to
     * include the package's actual complete license text, and may include copyright notices. The SPDX data creator
     * may use this field to record other acknowledgements, such as particular clauses from license texts, which may be
     * necessary or desirable to reproduce.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val attributionTexts: List<String> = emptyList(),

    /**
     * Checksums of the package.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val checksums: List<SpdxChecksum> = emptyList(),

    /**
     * Any general comments about the package.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = "",

    /**
     * A text relating to a copyright notice, even if not complete. To represent a not present value
     * [SpdxConstants.NONE] or [SpdxConstants.NOASSERTION] must be used.
     */
    val copyrightText: String,

    /**
     * A more detailed description of the package as opposed to [summary], which may be an extract from the package.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val description: String = "",

    /**
     * The download location as URL. To represent a not present value [SpdxConstants.NONE] or
     * [SpdxConstants.NOASSERTION] must be used.
     */
    val downloadLocation: String,

    /**
     * References to external sources of additional information.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val externalRefs: List<SpdxExternalReference> = emptyList(),

    /**
     * Indicates whether the file contents of the package have been used for the creation of the associated
     * [SpdxDocument].
     */
    val filesAnalyzed: Boolean = true,

    /**
     * The Spdx references to the files belonging to the package in the format "SPDXRef-${id-string}".
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val hasFiles: List<String> = emptyList(),

    /**
     * The homepage URL.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val homepage: String = "",

    /**
     * Any relevant background references or analysis that went in to arriving at the concluded License for the package.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val licenseComments: String = "",

    /**
     * The concluded license for the package as SPDX expression. To represent a not present value [SpdxConstants.NONE]
     * or [SpdxConstants.NOASSERTION] must be used.
     */
    val licenseConcluded: String,

    /**
     * The declared license for the package as SPDX expression. To represent a not present value [SpdxConstants.NONE] or
     * [SpdxConstants.NOASSERTION] must be used.
     */
    val licenseDeclared: String,

    /**
     * The license information found in the package as SPDX expression. To represent a not present value
     * [SpdxConstants.NONE] or [SpdxConstants.NOASSERTION] must be used.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val licenseInfoFromFiles: List<String> = emptyList(),

    /**
     * The name of the package.
     */
    val name: String,

    /**
     * Identifies from where or whom the package originally came. The value must be "NOASSERTION" or a single line text
     * in one of the following formats:
     *
     * 1. "Person: person name" or "Person: person name (email)"
     * 2. "Organization: organization name" or "Organization: organization name (email)"
     *
     * TODO: Introduce a data type for above subjects.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val originator: String? = null,

    /**
     * The actual file name of the package, or path of the directory being treated as a package.
     */
    @JsonProperty("packageFileName")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val packageFilename: String = "",

    /**
     * The [SpdxPackageVerificationCode] for the package.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val packageVerificationCode: SpdxPackageVerificationCode? = null,

    /**
     * Any relevant background information or additional comments about the origin of the package.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val sourceInfo: String = "",

    /**
     * A short description of the package.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val summary: String = "",

    /**
     * The distribution source for the package. The value must be "NOASSERTION" or a single line of text in one of the
     * following formats:
     *
     * 1. "Person: person name" or "Person: person name (email)"
     * 2. "Organization: organization name" or "Organization: organization name (email)"
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val supplier: String? = null,

    /**
     * The version of the package.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val versionInfo: String = ""
) {
    init {
        require(spdxId.startsWith(SpdxConstants.REF_PREFIX)) {
            "The SPDX ID '$spdxId' has to start with '${SpdxConstants.REF_PREFIX}'."
        }

        require(copyrightText.isNotBlank()) { "The copyright text must not be blank." }

        require(downloadLocation.isNotBlank()) { "The download location must not be blank." }

        require(name.isNotBlank()) { "The name must not be blank." }

        val validPrefixes = listOf(SpdxConstants.PERSON, SpdxConstants.ORGANIZATION)

        if (originator != null) {
            require(originator == SpdxConstants.NOASSERTION || validPrefixes.any { originator.startsWith(it) }) {
                "If specified, the originator has to start with any of $validPrefixes or be set to 'NOASSERTION'."
            }
        }

        if (supplier != null) {
            require(supplier == SpdxConstants.NOASSERTION || validPrefixes.any { supplier.startsWith(it) }) {
                "If specified, the supplier has to start with any of $validPrefixes or be set to 'NOASSERTION'."
            }
        }

        // TODO: The check for [licenseInfoFromFiles] can be made more strict, but the SPDX specification is not exact
        //       enough yet to do this safely.
        licenseInfoFromFiles.filterNot { it.isSpdxExpressionOrNotPresent() }.let {
            require(it.isEmpty()) {
                "The entries in licenseInfoFromFiles must each be either an SpdxExpression, 'NONE' or 'NOASSERTION', " +
                        "but found ${it.joinToString()}."
            }
        }
    }
}
