/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import org.ossreviewtoolkit.utils.spdxexpression.SpdxSingleLicenseExpression

data class LicenseTextCuration(
    /**
     * The SPDX license identifier to which to add license text(s) e.g., BSD-3-Clause or
     * GPL-2.0-or-later WITH SANE-exception. Accepts SPDX expressions using WITH, but not AND/OR.
     */
    val licenseId: SpdxSingleLicenseExpression,

    /**
     * A plain-text comment about this license text curation. Should contain information about how and why this
     * license text curation was created.
     */
    val comment: String? = null,

    /**
     * The license text (variant) to associate with the corresponding [licenseId] and [identifier][PackageCuration.id].
     */
    val licenseText: String
) {
    init {
        require(licenseText.isNotBlank()) {
            "The license text must not be blank."
        }
    }
}
