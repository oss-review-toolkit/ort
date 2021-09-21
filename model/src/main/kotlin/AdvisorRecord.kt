/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
 * Copyright (C) 2021 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef

import java.util.SortedMap

/**
 * A record of a single run of the advisor tool, containing the input and the [Finding] for every checked package.
 */
@JsonIgnoreProperties(value = ["has_issues"], allowGetters = true)
data class AdvisorRecord(
    /**
     * The [AdvisorResult]s for all [Package]s.
     */
    @JsonDeserialize(using = AdvisorResultsDeserializer::class)
    val advisorResults: SortedMap<Identifier, List<AdvisorResult>>
) {
    fun collectIssues(): Map<Identifier, Set<OrtIssue>> {
        val collectedIssues = mutableMapOf<Identifier, MutableSet<OrtIssue>>()

        advisorResults.forEach { (id, results) ->
            results.forEach { result ->
                collectedIssues.getOrPut(id) { mutableSetOf() } += result.summary.issues
            }
        }

        return collectedIssues
    }

    /**
     * True if any of the [advisorResults] contain [OrtIssue]s.
     */
    val hasIssues by lazy {
        advisorResults.any { (_, results) ->
            results.any { it.summary.issues.isNotEmpty() }
        }
    }

    /**
     * Return a list with all [Finding] objects that have been found for the given [package][pkgId]. Results
     * from different advisors are merged if necessary.
     */
    fun getVulnerabilities(pkgId: Identifier): List<Finding> =
        advisorResults[pkgId].orEmpty().flatMap { it.vulnerabilities }.mergeVulnerabilities()
}

/**
 * Merge this list of [Finding] objects by combining vulnerabilities with the same ID and merging their
 * references.
 */
private fun Collection<Finding>.mergeVulnerabilities(): List<Finding> {
    val vulnerabilitiesById = groupByTo(sortedMapOf()) { it.id }
    return vulnerabilitiesById.map { it.value.mergeReferences() }
}

/**
 * Merge this (non-empty) list of [Finding] objects (which are expected to have the same ID) by to a single
 * [Finding] that contains all the references from the source vulnerabilities (with duplicates removed).
 */
private fun Collection<Finding>.mergeReferences(): Finding {
    val references = flatMapTo(mutableSetOf()) { it.references }
    return Finding(first().id, references.toList())
}

/**
 * A custom deserializer to support deserialization of old [AdvisorRecord]s where [AdvisorRecord.advisorResults] was a
 * `List<AdvisorResultContainer>`.
 */
private class AdvisorResultsDeserializer : StdDeserializer<SortedMap<Identifier, List<AdvisorResult>>>(
    SortedMap::class.java
) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SortedMap<Identifier, List<AdvisorResult>> =
        if (p.currentToken == JsonToken.START_ARRAY) {
            val containers = jsonMapper.readValue(p, jacksonTypeRef<List<AdvisorResultContainer>>())
            containers.associateTo(sortedMapOf()) { it.id to it.results }
        } else {
            jsonMapper.readValue(p, jacksonTypeRef<SortedMap<Identifier, List<AdvisorResult>>>())
        }
}
