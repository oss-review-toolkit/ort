/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.spdxexpression

import org.ossreviewtoolkit.utils.spdx.SpdxConstants.DOCUMENT_REF_PREFIX
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.LICENSE_REF_PREFIX

private val EXCEPTION_STRING_REGEX = Regex(
    "\\b(exception|additional-terms|no-patent)\\b",
    RegexOption.IGNORE_CASE
)

/**
 * An SPDX expression for a license reference [id] as defined by version 2.2 of the [SPDX specification, annex D][1].
 *
 * [1]: https://spdx.github.io/spdx-spec/v2.2.2/SPDX-license-expressions/
 */
data class SpdxLicenseReferenceExpression(
    val id: String
) : SpdxSimpleExpression() {
    override fun decompose() = setOf(this)

    override fun simpleLicense() = id

    override fun exception(): String? = null

    override fun normalize() = this

    override fun validate(strictness: Strictness) {
        val isLicenseRef = id.startsWith(LICENSE_REF_PREFIX)
        val isDocumentRefToLicenseRef = id.startsWith(DOCUMENT_REF_PREFIX) && ":$LICENSE_REF_PREFIX" in id
        if (!isLicenseRef && !isDocumentRefToLicenseRef) throw SpdxException("'$id' is not an SPDX license reference.")
    }

    override fun equals(other: Any?) =
        when (other) {
            is SpdxLicenseReferenceExpression -> id.equals(other.id, ignoreCase = true)
            else -> false
        }

    override fun hashCode() = id.hashCode()

    override fun toString() = id

    override fun getLicenseUrl(): String? = null

    /** Return whether this LicenseRef is an exception. */
    fun isException(): Boolean = isValid(Strictness.ALLOW_LICENSEREF_EXCEPTIONS) && EXCEPTION_STRING_REGEX in id
}
