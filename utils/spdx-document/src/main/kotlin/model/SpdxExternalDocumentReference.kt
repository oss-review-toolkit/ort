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

import org.ossreviewtoolkit.utils.spdx.SpdxConstants

/**
 * A reference from one [SpdxDocument] to another external [SpdxDocument].
 * See https://spdx.github.io/spdx-spec/v2.3/document-creation-information/#66-external-document-references-field.
 */
data class SpdxExternalDocumentReference(
    /**
     * The identifier referencing the external [SpdxDocument] in the format: "DocumentRef-${id-string}"
     */
    val externalDocumentId: String,

    /**
     * The SPDX Document URI of the external [SpdxDocument] referenced by this [SpdxExternalDocumentReference].
     */
    val spdxDocument: String,

    /**
     * The checksum corresponding to the external [SpdxDocument] referenced by this [SpdxExternalDocumentReference].
     */
    val checksum: SpdxChecksum
) {
    init {
        validate()
    }

    fun validate(): SpdxExternalDocumentReference =
        apply {
            require(externalDocumentId.isNotBlank()) { "The external document ID must not be blank." }

            require(externalDocumentId.startsWith(SpdxConstants.DOCUMENT_REF_PREFIX)) {
                "The external document ID must start with '${SpdxConstants.DOCUMENT_REF_PREFIX}'."
            }

            require(spdxDocument.isNotEmpty()) { "The SPDX document must not be empty." }

            require(spdxDocument.trim() == spdxDocument) {
                "The SPDX document must not contain any leading or trailing whitespace."
            }
        }
}
