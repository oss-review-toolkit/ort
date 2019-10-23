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

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.treeToValue

import java.time.Instant
import java.util.SortedSet
import java.util.TreeSet

import kotlin.reflect.KClass

/**
 * A short summary of the scan results.
 */
@JsonDeserialize(using = ScanSummaryDeserializer::class)
data class ScanSummary(
    /**
     * The time when the scan started.
     */
    @JsonAlias("startTime")
    val startTime: Instant,

    /**
     * The time when the scan finished.
     */
    @JsonAlias("endTime")
    val endTime: Instant,

    /**
     * The number of scanned files.
     */
    @JsonAlias("fileCount")
    val fileCount: Int,

    /**
     * The [SPDX package verification code](https://spdx.org/spdx_specification_2_0_html#h.2p2csry), calculated from
     * all files in the package. Note that if the scanner is configured to ignore certain files they will still be
     * included in the calculation of this code.
     */
    val packageVerificationCode: String,

    /**
     * The license findings.
     */
    @JsonProperty("licenses")
    val licenseFindings: SortedSet<LicenseFinding>,

    /**
     * The copyright findings.
     */
    @JsonProperty("copyrights")
    val copyrightFindings: SortedSet<CopyrightFinding>,

    /**
     * The list of errors that occurred during the scan.
     */
    // Do not serialize if empty to reduce the size of the result file. If there are no errors at all,
    // [ScanRecord.hasErrors] already contains that information.
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val errors: List<OrtIssue> = emptyList()
) {
    @get:JsonIgnore
    val licenses: Set<String> = licenseFindings.mapTo(mutableSetOf()) { it.license }
}

class ScanSummaryDeserializer : StdDeserializer<ScanSummary>(OrtIssue::class.java) {
    private inline fun <reified T> JsonNode.readValue(property: String): T? =
        if (has(property)) {
            jsonMapper.treeToValue(this[property])
        } else {
            null
        }

    private inline fun <reified T : Any> JsonNode.readValues(property: String, kClass: KClass<T>): List<T> =
        this[property]?.map {
            jsonMapper.treeToValue(it, kClass.java)
        }.orEmpty()

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ScanSummary {
        val node = p.codec.readTree<JsonNode>(p)

        val (legacyLicenseFindings, legacyCopyrightFindings) = deserializeLegacyFindings(node)
        val licenseFindings = node.readValues("licenses", LicenseFinding::class)
        val copyrightFindings = node.readValues("copyrights", CopyrightFinding::class)

        // TODO: Remove the fallback value for packageVerification code once any ORT feature depends on its existence,
        //       as it is only there for backward compatibility.
        return ScanSummary(
            startTime = node.readValue("start_time")!!,
            endTime = node.readValue("end_time")!!,
            fileCount = node.readValue("file_count")!!,
            packageVerificationCode = node.readValue<String>("package_verification_code").orEmpty(),
            licenseFindings = (licenseFindings + legacyLicenseFindings).toSortedSet(),
            copyrightFindings = (copyrightFindings + legacyCopyrightFindings).toSortedSet(),
            errors = node.readValues("errors", OrtIssue::class)
        )
    }

    private fun deserializeLegacyFindings(node: JsonNode): Pair<List<LicenseFinding>, List<CopyrightFinding>> {
        val licenseFindings = mutableListOf<LicenseFinding>()
        val copyrightFindings = mutableListOf<CopyrightFinding>()

        node["license_findings"]?.forEach { licenseNode ->
            val license = licenseNode.readValue<String>("license")!!

            deserializeLocations(licenseNode).apply {
                require(isNotEmpty()) { "License findings without location are not supported anymore." }

                forEach {
                    licenseFindings.add(LicenseFinding(license = license, location = it))
                }
            }

            licenseNode["copyrights"]?.forEach { copyrightsNode ->
                val statement = copyrightsNode.readValue<String>("statement")!!
                deserializeLocations(copyrightsNode).apply {
                    require(isNotEmpty()) { "License findings without location are not supported anymore." }

                    forEach {
                        copyrightFindings.add(CopyrightFinding(statement = statement, location = it))
                    }
                }
            }
        }

        return Pair(licenseFindings, copyrightFindings)
    }

    private fun deserializeLocations(node: JsonNode) =
        node["locations"]?.let { locations ->
            jsonMapper.readValue<TreeSet<TextLocation>>(
                jsonMapper.treeAsTokens(locations),
                TextLocation.TREE_SET_TYPE
            )
        } ?: sortedSetOf()
}
