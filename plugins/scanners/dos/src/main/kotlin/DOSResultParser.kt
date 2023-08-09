/*
 * SPDX-FileCopyrightText: 2023 HH Partners
 *
 * SPDX-License-Identifier: MIT
 */

/**
 * This file implements the needed functions to interpret the scan results from DOS API
 * to a format suited for ORT.
 */
package org.ossreviewtoolkit.plugins.scanners.dos

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.ossreviewtoolkit.model.*

import java.time.Instant

internal fun generateSummary(startTime: Instant, endTime: Instant, jsonString: String): ScanSummary {
    val mapper = ObjectMapper()
    val result: JsonNode = mapper.readTree(jsonString)
    val licenseFindings = getLicenseFindings(result)
    val copyrightFindings = getCopyrightFindings(result)
    val issues = getIssues(result)

    return ScanSummary(
        startTime,
        endTime,
        licenseFindings,
        copyrightFindings,
        emptySet(),
        issues
    )
}
private fun getLicenseFindings(result: JsonNode): Set<LicenseFinding> {
    val licenseFindings = mutableListOf<LicenseFinding>()
    val licenses = result["licenses"] ?: return emptySet()

    licenses.forEach { licenseNode ->
        val license = licenseNode["license"].asText()
        val location = licenseNode["location"]
        val path = location["path"].asText()
        val startLine = location["start_line"].asInt()
        val endLine = location["end_line"].asInt()
        val score = licenseNode["score"].asDouble()
        licenseFindings.add(
            LicenseFinding(
                license,
                TextLocation(
                    path,
                    startLine,
                    endLine
                ),
                score.toFloat()
            )
        )
    }

    return licenseFindings.toSet()
}

private fun getCopyrightFindings(result: JsonNode): Set<CopyrightFinding> {
    val copyrightFindings = mutableListOf<CopyrightFinding>()
    val copyrights = result["copyrights"] ?: return emptySet()

    copyrights.forEach { copyrightNode ->
        val statement = copyrightNode["statement"].asText()
        val location = copyrightNode["location"]
        val path = location["path"].asText()
        val startLine = location["start_line"].asInt()
        val endLine = location["end_line"].asInt()
        copyrightFindings.add(
            CopyrightFinding(
                statement,
                TextLocation(
                    path,
                    startLine,
                    endLine
                )
            )
        )
    }

    return copyrightFindings.toSet()
}

private fun getIssues(result: JsonNode): List<Issue> {
    val issueFindings = mutableListOf<Issue>()
    val issues = result["issues"] ?: return emptyList()

    issues.forEach { issueNode ->
        val timestamp = Instant.parse(issueNode["timestamp"].asText())
        val source = issueNode["source"].asText()
        val message = issueNode["message"].asText()
        val severity = Severity.valueOf(issueNode["severity"].asText().uppercase())

        issueFindings.add(
            Issue(
                timestamp,
                source,
                message,
                severity
            )
        )
    }

    return issueFindings
}
