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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.treeToValue

import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.utils.CopyrightStatementsProcessor
import com.here.ort.utils.SortedSetComparator
import com.here.ort.utils.textValueOrEmpty

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
data class LicenseFinding(
        val license: String,
        val copyrights: SortedSet<String>
) : Comparable<LicenseFinding> {
    companion object {
        private val COPYRIGHTS_COMPARATOR = SortedSetComparator<String>()
    }

    override fun compareTo(other: LicenseFinding) =
            when {
                license != other.license -> license.compareTo(other.license)
                else -> COPYRIGHTS_COMPARATOR.compare(copyrights, other.copyrights)
            }
}

/**
 * Custom deserializer to support old versions of the [LicenseFinding] class.
 */
class LicenseFindingDeserializer : StdDeserializer<LicenseFinding>(LicenseFinding::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LicenseFinding {
        val node = p.codec.readTree<JsonNode>(p)
        return when {
            node.isTextual -> LicenseFinding(node.textValueOrEmpty(), sortedSetOf())
            else -> {
                val license = jsonMapper.treeToValue<String>(node["license"])
                val copyrights = jsonMapper.treeToValue<SortedSet<String>>(node["copyrights"])
                return LicenseFinding(license, copyrights)
            }
        }
    }
}
