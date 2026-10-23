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

import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxLicense

/**
 * An SPDX expression for a license [id] as defined by version 2.2 of the [SPDX specification, annex D][1].
 * [orLaterVersion] indicates whether the license id also describes later versions of the license.
 *
 * [1]: https://spdx.github.io/spdx-spec/v2.2.2/SPDX-license-expressions/
 */
class SpdxLicenseIdExpression(
    val id: String,
    val orLaterVersion: Boolean = false
) : SpdxSimpleExpression() {
    private val spdxLicense by lazy { SpdxLicense.forId(id) }

    override fun decompose() = setOf(this)

    override fun simpleLicense() = toString()

    override fun exception(): String? = null

    override fun normalize() = SpdxSimpleLicenseMapper.getInstance().map(toString()) ?: this

    override fun validate(strictness: Strictness) {
        val isValid = SpdxConstants.isNotPresent(id) || when (strictness) {
            Strictness.ALLOW_ANY -> true
            Strictness.ALLOW_DEPRECATED -> spdxLicense != null
            Strictness.ALLOW_CURRENT, Strictness.ALLOW_LICENSEREF_EXCEPTIONS -> spdxLicense?.deprecated == false
        }

        if (!isValid) throw SpdxException("'$this' is not a valid SPDX license id.")
    }

    override fun equals(other: Any?) =
        when (other) {
            is SpdxLicenseIdExpression -> {
                id.equals(other.id, ignoreCase = true) && orLaterVersion == other.orLaterVersion
            }

            else -> false
        }

    override fun hashCode() = id.hashCode() + 31 * orLaterVersion.hashCode()

    override fun toString() =
        buildString {
            append(id)
            // While in the current SPDX standard the "or later version" semantic is part of the id string itself,
            // it is a generic "+" operator for deprecated licenses.
            if (orLaterVersion && !id.endsWith("-or-later")) append("+")
        }

    override fun getLicenseUrl() = if (isValid()) SpdxConstants.LICENSE_LIST_URL + id else null
}
