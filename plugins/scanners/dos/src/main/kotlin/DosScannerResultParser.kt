/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.dos

import java.time.Instant

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.spdx.toSpdx

internal fun generateSummary(startTime: Instant, endTime: Instant, result: JsonObject): ScanSummary {
    val issues = mutableListOf<Issue>()

    val licenseFindings = result.getLicenseFindings(issues)
    val copyrightFindings = result.getCopyrightFindings(issues)

    result.getIssues(issues)

    return ScanSummary(
        startTime,
        endTime,
        licenseFindings = licenseFindings,
        copyrightFindings = copyrightFindings,
        issues = issues
    )
}

private fun JsonObject.getLicenseFindings(issues: MutableList<Issue>): Set<LicenseFinding> {
    val licenses = get("licenses")?.jsonArray ?: return emptySet()

    return licenses.mapNotNullTo(mutableSetOf()) {
        val licenseNode = it.jsonObject

        val license = licenseNode.getValue("license").jsonPrimitive.content
        val score = licenseNode.getValue("score").jsonPrimitive.float

        runCatching {
            license.toSpdx()
        }.onFailure { exception ->
            issues += Issue(
                source = "DOSResultParser",
                message = "Cannot parse '$license' as an SPDX expression: ${exception.message}"
            )
        }.mapCatching { licenseExpression ->
            val location = licenseNode.getTextLocation()
            LicenseFinding(licenseExpression, location, score)
        }.onFailure { exception ->
            issues += Issue(
                source = "DOSResultParser",
                message = "Failed to create a text location for $licenseNode: ${exception.message}"
            )
        }.getOrNull()
    }
}

private fun JsonObject.getCopyrightFindings(issues: MutableList<Issue>): Set<CopyrightFinding> {
    val copyrights = get("copyrights")?.jsonArray ?: return emptySet()

    return copyrights.mapNotNullTo(mutableSetOf()) {
        val copyrightNode = it.jsonObject

        val statement = copyrightNode.getValue("statement").jsonPrimitive.content

        runCatching {
            val location = copyrightNode.getTextLocation()
            CopyrightFinding(statement, location)
        }.onFailure { exception ->
            issues += Issue(
                source = "DOSResultParser",
                message = "Failed to create a text location for $copyrightNode: ${exception.message}"
            )
        }.getOrNull()
    }
}

private fun JsonObject.getTextLocation(): TextLocation {
    val location = getValue("location").jsonObject

    val path = location.getValue("path").jsonPrimitive.content
    val startLine = location.getValue("start_line").jsonPrimitive.int
    val endLine = location.getValue("end_line").jsonPrimitive.int

    return TextLocation(path, startLine, endLine)
}

private fun JsonObject.getIssues(issues: MutableList<Issue>) {
    get("issues")?.jsonArray.orEmpty().mapTo(issues) {
        val issueNode = it.jsonObject
        val timestamp = Instant.parse(issueNode.getValue("timestamp").jsonPrimitive.content)
        val source = issueNode.getValue("source").jsonPrimitive.content
        val message = issueNode.getValue("message").jsonPrimitive.content
        val severity = Severity.valueOf(issueNode.getValue("severity").jsonPrimitive.content.uppercase())

        Issue(timestamp, source, message, severity)
    }
}
