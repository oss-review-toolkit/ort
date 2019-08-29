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

package com.here.ort.model

import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.utils.CopyrightStatementsProcessor

import java.util.SortedMap
import java.util.SortedSet

/**
 * A map that associates licenses with their belonging copyrights. This is provided mostly for convenience as creating
 * a similar collection based on the [LicenseFindings] class is a bit cumbersome due to its required layout to support
 * legacy serialized formats.
 */
typealias LicenseFindingsMap = SortedMap<String, MutableSet<String>>

fun LicenseFindingsMap.processStatements() =
    mapValues { (_, copyrights) ->
        CopyrightStatementsProcessor().process(copyrights).toMutableSet()
    }.toSortedMap()

fun LicenseFindingsMap.removeGarbage(copyrightGarbage: CopyrightGarbage) =
    mapValues { (_, copyrights) ->
        copyrights.filterNot {
            it in copyrightGarbage.items
        }.toMutableSet()
    }.toSortedMap()

/**
 * A class to store a [license] finding along with its belonging [copyrights] and the [locations] where the license was
 * found.
 */
data class LicenseFindings(
    val license: String,
    val locations: SortedSet<TextLocation>,
    val copyrights: SortedSet<CopyrightFindings>
) : Comparable<LicenseFindings> {
    override fun compareTo(other: LicenseFindings) =
        compareValuesBy(
            this,
            other,
            compareBy(LicenseFindings::license)
                .thenBy(TextLocation.SORTED_SET_COMPARATOR, LicenseFindings::locations)
                .thenBy(CopyrightFindings.SORTED_SET_COMPARATOR, LicenseFindings::copyrights)
        ) { it }
}
