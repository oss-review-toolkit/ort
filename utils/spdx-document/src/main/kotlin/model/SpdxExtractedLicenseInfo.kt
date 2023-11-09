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

/**
 * Information about other licenses that are not found on the SPDX license list.
 * See https://spdx.github.io/spdx-spec/v2.3/other-licensing-information-detected/.
 *
 * Note: The above specification says that several fields are mandatory only if the license is not on the SPDX license
 * list and at the same time says that instances of this class should only be created for licenses not on the SPDX
 * license list. Thus make the respective fields unconditionally mandatory in order to address both of the mentioned
 * constraints.
 */
data class SpdxExtractedLicenseInfo(
    /**
     * A general comment about the license referred to by [licenseId].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = "",

    /**
     * The actual text of the license reference extracted from the package or file that is associated with the License
     * Identifier to aid in future analysis.
     */
    val extractedText: String,

    /**
     * A locally unique identifier for a license that is not on the SPDX License List.
     */
    val licenseId: String,

    /**
     * The common name for the license referred to by [licenseId].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val name: String = "",

    /**
     * A list of URLs pointing to the official source of the license referred to by [licenseId].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val seeAlsos: List<String> = emptyList()
) {
    init {
        validate()
    }

    fun validate(): SpdxExtractedLicenseInfo =
        apply {
            require(licenseId.isNotBlank()) {
                "The license ID must not be blank (the optional name is '$name')."
            }

            require(extractedText.isNotBlank()) {
                "The extracted text must not be blank (the license ID is '$licenseId')."
            }
        }
}
