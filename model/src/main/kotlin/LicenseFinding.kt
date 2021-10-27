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

package org.ossreviewtoolkit.model

import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

/**
 * A class representing a license finding. License findings can point to single licenses or to complex
 * [SpdxExpression]s, depending on the capabilities of the used license scanner. [LicenseFindingCuration]s can also be
 * used to create findings with complex expressions.
 */
data class LicenseFinding(
    /**
     * The found SPDX expression.
     */
    val license: SpdxExpression,

    /**
     * The text location where the license was found.
     */
    val location: TextLocation
) : Comparable<LicenseFinding> {
    companion object {
        private val COMPARATOR = compareBy<LicenseFinding> { it.license.toString() }.thenBy(LicenseFinding::location)
    }

    constructor(license: String, location: TextLocation) : this(license.toSpdx(), location)

    override fun compareTo(other: LicenseFinding) = COMPARATOR.compare(this, other)
}
