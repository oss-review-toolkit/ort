/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.licenses

import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * A resolved original expression.
 */
data class ResolvedOriginalExpression(
    /**
     * The license expression.
     */
    val expression: SpdxExpression,

    /**
     * The license source.
     */
    val source: LicenseSource,

    /**
     * Indicate whether all license findings corresponding to [expression] are excluded. Must be false if [source] does
     * not equal [LicenseSource.DETECTED].
     */
    val isDetectedExcluded: Boolean = false
) {
    init {
        require(!isDetectedExcluded || source == LicenseSource.DETECTED) {
            "For license source '$source' the flag isDetectedExcluded must not be set."
        }
    }
}
