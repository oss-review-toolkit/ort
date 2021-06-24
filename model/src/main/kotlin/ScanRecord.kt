/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef

import java.util.SortedMap

/**
 * A record of a single run of the scanner tool, containing the input and the scan results for all scanned packages.
 */
@JsonIgnoreProperties(
    value = ["has_issues", /* Backwards-compatibility: */ "has_errors", "scanned_scopes", "scopes"],
    allowGetters = true
)
data class ScanRecord(
    /**
     * The [ScanResult]s for all [Package]s.
     */
    @JsonDeserialize(using = ScanResultsDeserializer::class)
    val scanResults: SortedMap<Identifier, List<ScanResult>>,

    /**
     * The [AccessStatistics] for the scan results storage.
     */
    @JsonAlias("cache_stats")
    val storageStats: AccessStatistics
) {
    /**
     * Return a map of all de-duplicated [OrtIssue]s associated by [Identifier].
     */
    fun collectIssues(): Map<Identifier, Set<OrtIssue>> {
        val collectedIssues = mutableMapOf<Identifier, MutableSet<OrtIssue>>()

        scanResults.forEach { (id, results) ->
            results.forEach { result ->
                collectedIssues.getOrPut(id) { mutableSetOf() } += result.summary.issues
            }
        }

        return collectedIssues
    }

    /**
     * True if any of the [scanResults] contain [OrtIssue]s.
     */
    val hasIssues by lazy {
        scanResults.any { (_, results) ->
            results.any { it.summary.issues.isNotEmpty() }
        }
    }
}

/**
 * A custom deserializer to support deserialization of old [ScanRecord]s where [ScanRecord.scanResults] was a
 * `List<ScanResultContainer>`.
 */
private class ScanResultsDeserializer : StdDeserializer<SortedMap<Identifier, List<ScanResult>>>(
    SortedMap::class.java
) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SortedMap<Identifier, List<ScanResult>> =
        if (p.currentToken == JsonToken.START_ARRAY) {
            val containers = jsonMapper.readValue(p, jacksonTypeRef<List<ScanResultContainer>>())
            containers.associateTo(sortedMapOf()) { it.id to it.results }
        } else {
            jsonMapper.readValue(p, jacksonTypeRef<SortedMap<Identifier, List<ScanResult>>>())
        }
}
