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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize

import org.ossreviewtoolkit.utils.common.getDuplicates
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.REF_PREFIX
import org.ossreviewtoolkit.utils.spdx.SpdxLicense

const val SPDX_VERSION_2_2 = "SPDX-2.2"
const val SPDX_VERSION_2_3 = "SPDX-2.3"

private const val SPDX_ID = "${REF_PREFIX}DOCUMENT"

private val DATA_LICENSE = SpdxLicense.CC0_1_0.id

/**
 * An SPDX document as specified by https://spdx.github.io/spdx-spec/v2.3/.
 */
data class SpdxDocument(
    /**
     * Identifier of this [SpdxDocument] which may be referenced in relationships by other files, packages internally
     * and documents externally.
     *
     * TODO: Introduce a dedicated type.
     */
    @JsonProperty("SPDXID")
    val spdxId: String = SPDX_ID,

    /**
     * The SPDX version of this document, must equal [SPDX_VERSION_MAJOR_MINOR].
     */
    val spdxVersion: String = SPDX_VERSION_2_3,

    /**
     * Information about the creation of this document.
     */
    val creationInfo: SpdxCreationInfo,

    /**
     * The name of this [SpdxDocument] as a single line.
     */
    val name: String,

    /**
     * The data license of this document, must equal [DATA_LICENSE].
     */
    val dataLicense: String = DATA_LICENSE,

    /**
     * A comment towards the consumers of this [SpdxDocument] as multi-line text.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = "",

    /**
     * A listing of any external [SpdxDocument] referenced from within this [SpdxDocument].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val externalDocumentRefs: List<SpdxExternalDocumentReference> = emptyList(),

    /**
     * Information about any licenses which are not on the SPDX license list.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val hasExtractedLicensingInfos: List<SpdxExtractedLicenseInfo> = emptyList(),

    /**
     * The [SpdxAnnotation]s for the [SpdxDocument].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val annotations: List<SpdxAnnotation> = emptyList(),

    /**
     * A unique absolute Uniform Resource Identifier (URI) as specified in RFC-3986, with the following
     * exceptions:
     *
     *  - The SPDX Document URI cannot contain a URI "part" (e.g. the # delimiter), since the # is used to uniquely
     *    identify SPDX element identifiers. The URI must contain a scheme (e.g. "https").
     *  - The URI must be unique for the SPDX document including the specific version of the SPDX document. If the SPDX
     *    document is updated, thereby creating a new version, a new URI for the updated document must be used. There
     *    can only be one URI for an SPDX document and only one SPDX document for a given URI.
     */
    val documentNamespace: String,

    /**
     * All SPDX identifiers of all packages and files contained in [packages] and [files].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val documentDescribes: List<String> = emptyList(),

    /**
     * All packages described in this [SpdxDocument].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val packages: List<SpdxPackage> = emptyList(),

    /**
     * All files described in this [SpdxDocument].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val files: List<SpdxFile> = emptyList(),

    /**
     * All snippets described in this [SpdxDocument].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val snippets: List<SpdxSnippet> = emptyList(),

    /**
     * All relationships described in this [SpdxDocument].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(converter = SpdxRelationshipSortedSetConverter::class)
    val relationships: List<SpdxRelationship> = emptyList()
) {
    init {
        validate()
    }

    fun validate(): SpdxDocument =
        apply {
            require(spdxId.isNotBlank()) { "The SPDX-ID must not be blank." }

            require(spdxVersion.isNotBlank()) { "The SPDX version must not be blank." }

            require(name.isNotBlank()) { "The document name for SPDX-ID '$spdxId' must not be blank." }

            require(dataLicense.isNotBlank()) { "The data license must not be blank." }

            require(packages.isNotEmpty()) { "At least one package must be listed in packages" }

            val duplicateExternalDocumentRefs = externalDocumentRefs.getDuplicates { it.externalDocumentId }
            require(duplicateExternalDocumentRefs.isEmpty()) {
                "The document must not contain duplicate external document references but has " +
                    "${duplicateExternalDocumentRefs.keys}."
            }

            require(documentNamespace.isNotBlank()) { "The document namespace must not be blank." }

            val duplicatePackages = packages.getDuplicates { it.spdxId }
            require(duplicatePackages.isEmpty()) {
                "The document must not contain duplicate packages but has ${duplicatePackages.keys}."
            }

            val duplicateFiles = files.getDuplicates { it.spdxId }
            require(duplicateFiles.isEmpty()) {
                "The document must not contain duplicate files but has ${duplicateFiles.keys}."
            }

            val duplicateSnippets = snippets.getDuplicates { it.spdxId }
            require(duplicateSnippets.isEmpty()) {
                "The document must not contain duplicate snippets but has ${duplicateSnippets.keys}."
            }

            val hasDescribesRelationship = relationships.any { it.relationshipType == SpdxRelationship.Type.DESCRIBES }
            require(hasDescribesRelationship || documentDescribes.isNotEmpty()) {
                "The document must either have at least one relationship of type 'DESCRIBES' or contain the " +
                    "'documentDescribes' field."
            }
        }
}
