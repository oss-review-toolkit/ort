/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef

import java.net.URI

/**
 * A data class representing a finding from a concrete Advisor implementation.
 *
 * This class stores the information about a single finding, which may have been retrieved from multiple
 * advice providers. For each source of information a [FindingDetail] is contained.
 */
@JsonDeserialize(using = FindingDeserializer::class)
data class Finding(
    /**
     * The ID of this finding. This is typically an external ID, such as an CVE ID or an ID from a bug tracker.
     */
    val id: String,

    /**
     * A list with detailed information for this finding obtained from different sources.
     */
    val details: List<FindingDetail>
)

/**
 * A custom deserializer to support the deserialization of [Finding] instances using an older format, in which
 * detailed information was embedded into the class rather than externalized in [FindingDetail] objects.
 */
private class FindingDeserializer : StdDeserializer<Finding>(Finding::class.java) {
    override fun deserialize(p: JsonParser, ctx: DeserializationContext): Finding {
        val findingNode = p.codec.readTree<JsonNode>(p)
        val id = findingNode["id"].textValue()

        val details = findingNode.readDetails("details")
            ?: findingNode.readDetails("references")
            ?: findingNode.readLegacyDetails()
        return Finding(id, details)
    }
}

/**
 * Return a list with [FindingDetail]s read from the child with name [fieldName] or *null* if this field is not
 * present.
 */
private fun JsonNode.readDetails(fieldName: String): List<FindingDetail>? =
    this[fieldName]?.let { jsonMapper.convertValue(it, jacksonTypeRef<List<FindingDetail>>()) }

/**
 * Return a list of [FindingDetail]s obtained from a legacy JSON representation.
 */
private fun JsonNode.readLegacyDetails(): List<FindingDetail> {
    val severity = this["severity"].floatValue()
    val uri = this["url"].textValue()
    return listOf(FindingDetail(URI(uri), null, severity.toString()))
}
