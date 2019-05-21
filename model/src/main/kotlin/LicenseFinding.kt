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
import com.here.ort.utils.CopyrightStatementsNormalizer

import java.util.SortedSet
import java.util.TreeSet

typealias LicenseFindings = List<LicenseFinding>

/**
 * Remove all copyright findings whose statements are classified as garbage in [copyrightGarbage].
 */
fun LicenseFindings.removeCopyrightGarbage(copyrightGarbage: CopyrightGarbage) =
    map { finding ->
        finding.copy(copyrights = finding.copyrights.filterNot { it.statement in copyrightGarbage.items }.toSortedSet())
    }

/**
 * Merge all findings which have the same license.
 */
fun LicenseFindings.mergeByLicense() =
    groupBy { it.license }.values.map { findings ->
        findings.reduce { left, right ->
            val locations = (left.locations + right.locations).toSortedSet()
            val copyrights = (left.copyrights + right.copyrights).mergeByStatement().toSortedSet()
            LicenseFinding(left.license, locations, copyrights)
        }
    }

/**
 * Merge all copyright findings which have the same statement.
 */
fun Collection<CopyrightFinding>.mergeByStatement() =
    groupBy { it.statement }.values.map { findings ->
        findings.reduce { left, right ->
            CopyrightFinding(left.statement, (left.locations + right.locations).toSortedSet())
        }
    }

/**
 * A class to store a [license] finding along with its belonging [copyrights] and the [locations] where the license was
 * found.
 */
data class LicenseFinding(
    val license: String,
    val locations: SortedSet<TextLocation>,
    val copyrights: SortedSet<CopyrightFinding>
) : Comparable<LicenseFinding> {
    override fun compareTo(other: LicenseFinding) =
        compareValuesBy(
            this,
            other,
            compareBy(LicenseFinding::license)
                .thenBy(TextLocation.SORTED_SET_COMPARATOR, LicenseFinding::locations)
                .thenBy(CopyrightFinding.SORTED_SET_COMPARATOR, LicenseFinding::copyrights)
        ) { it }

    /**
     * Normalize all copyright findings using [CopyrightStatementsNormalizer.process].
     */
    fun normalizeCopyrightStatements(): Set<String> {
        val statements = copyrights.map { it.statement }
        return CopyrightStatementsNormalizer().normalize(statements).toMutableSet()
    }
}

/**
 * Custom deserializer to support old versions of the [LicenseFinding] class.
 */
class LicenseFindingDeserializer : StdDeserializer<LicenseFinding>(LicenseFinding::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LicenseFinding {
        val node = p.codec.readTree<JsonNode>(p)
        return when {
            node.isTextual -> LicenseFinding(node.textValue(), sortedSetOf(), sortedSetOf())
            else -> {
                val license = jsonMapper.treeToValue<String>(node["license"])

                val copyrights = jsonMapper.readValue<TreeSet<CopyrightFinding>>(
                    jsonMapper.treeAsTokens(node["copyrights"]),
                    CopyrightFinding.TREE_SET_TYPE
                )

                val locations = deserializeLocations(node)

                LicenseFinding(license, locations, copyrights)
            }
        }
    }

    private fun deserializeLocations(node: JsonNode) =
        node["locations"]?.let { locations ->
            jsonMapper.readValue<TreeSet<TextLocation>>(
                jsonMapper.treeAsTokens(locations),
                TextLocation.TREE_SET_TYPE
            )
        } ?: sortedSetOf()
}
