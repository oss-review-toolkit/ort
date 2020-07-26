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

import org.ossreviewtoolkit.utils.SortedSetComparator

// TODO: This class currently co-exists with the singular form class [CopyrightFinding] which can be confusing.
// The singular classes [CopyrightFinding] and [LicenseFinding] are the model of the scanner module while the plural
// form ones are the model of the output of [org.ossreviewtoolkit.model.utils.FindingsMatcher], thus instances of
// [CopyrightFindings] are used only when associated with (or rather referenced by) [LicenseFindings].
// The output format of [org.ossreviewtoolkit.model.utils.FindingsMatcher] needs to change as it misses the association
// between copyright finding and the actual text location of the matched license finding. Further the way copyright
// findings are matched to root licenses might also need to change which in turn may also require changing these plural
// form classes. Due to the above it seems to make sense to defer the clarification of singular vs. plural form
// copyright finding classes until the mentioned improvements to the matching have been applied.
data class CopyrightFindings(
    val statement: String,
    val locations: SortedSet<TextLocation>
) : Comparable<CopyrightFindings> {
    companion object {
        val SORTED_SET_COMPARATOR = SortedSetComparator<CopyrightFindings>()
        private val COMPARATOR = compareBy(CopyrightFindings::statement)
                .thenBy(TextLocation.SORTED_SET_COMPARATOR, CopyrightFindings::locations)
    }

    override fun compareTo(other: CopyrightFindings) = COMPARATOR.compare(this, other)
}
