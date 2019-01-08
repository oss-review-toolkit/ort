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

import com.fasterxml.jackson.annotation.JsonCreator

import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.utils.CopyrightStatementsProcessor

import java.util.SortedMap
import java.util.SortedSet

/**
 * A map that associates licenses with their belonging copyrights. This is provided mostly for convenience as creating
 * a similar collection based on the [LicenseFinding] class is a bit cumbersome due to its required layout to support
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
 * A class to store a [license] finding along with its belonging [copyrights]. To support deserializing older versions
 * of this class which did not include the copyrights a secondary constructor is only taking a [licenseName].
 */
data class LicenseFinding @JsonCreator constructor(
        val license: String,
        val copyrights: SortedSet<String>
) : Comparable<LicenseFinding> {
    companion object {
        private val COPYRIGHTS_COMPARATOR = Comparator<SortedSet<String>> { o1, o2 ->
            val iterator1 = o1.iterator()
            val iterator2 = o2.iterator()

            while (iterator1.hasNext() && iterator2.hasNext()) {
                val value1 = iterator1.next()
                val value2 = iterator2.next()

                value1.compareTo(value2).let {
                    if (it != 0) return@Comparator it
                }
            }

            return@Comparator when {
                iterator1.hasNext() -> 1
                iterator2.hasNext() -> -1
                else -> 0
            }
        }
    }

    @JsonCreator
    constructor(licenseName: String) : this(licenseName, sortedSetOf())

    override fun compareTo(other: LicenseFinding) =
            when {
                license != other.license -> license.compareTo(other.license)
                else -> COPYRIGHTS_COMPARATOR.compare(copyrights, other.copyrights)
            }
}
