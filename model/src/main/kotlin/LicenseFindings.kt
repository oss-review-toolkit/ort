/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import java.util.SortedSet

import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression

/**
 * A class to store a [license] finding along with its belonging [copyrights] and the [locations] where the license was
 * found.
 */
data class LicenseFindings(
    val license: SpdxSingleLicenseExpression,
    val locations: SortedSet<TextLocation>,
    val copyrights: SortedSet<CopyrightFindings>
) : Comparable<LicenseFindings> {
    companion object {
        private val COMPARATOR = compareBy<LicenseFindings> { it.license.toString() }
                .thenBy(TextLocation.SORTED_SET_COMPARATOR, LicenseFindings::locations)
                .thenBy(CopyrightFindings.SORTED_SET_COMPARATOR, LicenseFindings::copyrights)
    }

    override fun compareTo(other: LicenseFindings) = COMPARATOR.compare(this, other)
}
