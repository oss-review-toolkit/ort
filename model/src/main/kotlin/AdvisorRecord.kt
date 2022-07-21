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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef

import java.util.SortedMap

/**
 * Type alias for a function that allows filtering of [AdvisorResult]s.
 */
typealias AdvisorResultFilter = (AdvisorResult) -> Boolean

/**
 * A record of a single run of the advisor tool, containing the input and the [Vulnerability] for every checked package.
 */
@JsonIgnoreProperties(value = ["has_issues"], allowGetters = true)
data class AdvisorRecord(
    /**
     * The [AdvisorResult]s for all [Package]s.
     */
    @JsonDeserialize(using = AdvisorResultsDeserializer::class)
    val advisorResults: SortedMap<Identifier, List<AdvisorResult>>
) {
    companion object {
        /**
         * A filter for [AdvisorResult]s that matches only results that contain vulnerabilities.
         */
        val RESULTS_WITH_VULNERABILITIES: AdvisorResultFilter = { it.vulnerabilities.isNotEmpty() }

        /**
         * A filter for [AdvisorResult]s that matches only results that contain defects.
         */
        val RESULTS_WITH_DEFECTS: AdvisorResultFilter = { it.defects.isNotEmpty() }

        /**
         * Return a filter for [AdvisorResult]s that contain issues. Match only results with an issue whose severity
         * is greater or equal than [minSeverity]. Often, issues are only relevant for certain types of advisors. For
         * instance, when processing vulnerability information, it is not of interest if an advisor for defects had
         * encountered problems. Therefore, support an optional filter for a [capability] of the advisor that produced
         * a result.
         */
        fun resultsWithIssues(minSeverity: Severity = Severity.HINT, capability: AdvisorCapability? = null):
                    AdvisorResultFilter =
            { result ->
                (capability == null || capability in result.advisor.capabilities) &&
                        result.summary.issues.any { it.severity >= minSeverity }
            }
    }

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
     * Return a map of all [Package]s and the associated [Vulnerabilities][Vulnerability].
     */
    @JsonIgnore
    fun getVulnerabilities(): Map<Identifier, List<Vulnerability>> =
        advisorResults.mapValues { (_, results) ->
            results.flatMap { it.vulnerabilities }.mergeVulnerabilities()
        }

    /**
     * Return a list with all [Vulnerability] objects that have been found for the given [package][pkgId]. Results
     * from different advisors are merged if necessary.
     */
    fun getVulnerabilities(pkgId: Identifier): List<Vulnerability> =
        getFindings(pkgId) { it.vulnerabilities }.mergeVulnerabilities()

    /**
     * Return a list with all [Defect] objects that have been found for the given [package][pkgId]. If there are
     * results from different advisors, a union list is constructed. No merging is done, as it is expected that the
     * results from different advisors cannot be combined.
     */
    fun getDefects(pkgId: Identifier): List<Defect> = getFindings(pkgId) { it.defects }

    /**
     * Apply the given [filter] to the results stored in this record and return a map with the results that pass the
     * filter. When processing advisor results, often specific criteria are relevant, e.g. whether security
     * vulnerabilities were found or certain issues were detected. Using this function, it is easy to filter out only
     * those results matching such criteria.
     */
    fun filterResults(filter: AdvisorResultFilter): Map<Identifier, List<AdvisorResult>> =
        advisorResults.mapNotNull { (id, results) ->
            results.filter(filter).takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()

    /**
     * Helper function to obtain the findings of type [T] for the given [package][pkgId] using a [selector] function
     * to extract the desired field.
     */
    private fun <T> getFindings(pkgId: Identifier, selector: (AdvisorResult) -> List<T>): List<T> =
        advisorResults[pkgId].orEmpty().flatMap(selector)
}

/**
 * Merge this list of [Vulnerability] objects by combining vulnerabilities with the same ID and merging their
 * references.
 */
private fun Collection<Vulnerability>.mergeVulnerabilities(): List<Vulnerability> {
    val vulnerabilitiesById = groupByTo(sortedMapOf()) { it.id }
    return vulnerabilitiesById.map { it.value.mergeReferences() }
}

/**
 * Merge this (non-empty) list of [Vulnerability] objects (which are expected to have the same ID) by to a single
 * [Vulnerability] that contains all the references from the source vulnerabilities (with duplicates removed).
 */
private fun Collection<Vulnerability>.mergeReferences(): Vulnerability {
    val references = flatMapTo(mutableSetOf()) { it.references }
    return Vulnerability(id = first().id, references = references.toList())
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
