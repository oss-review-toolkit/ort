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

internal fun generateSummary(startTime: Instant, endTime: Instant, result: JsonObject): ScanSummary {
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
private fun getLicenseFindings(result: JsonObject): Set<LicenseFinding> {
    val licenses = result["licenses"]?.jsonArray ?: return emptySet()

    return licenses.mapTo(mutableSetOf()) {
        val licenseNode = it.jsonObject

        val license = licenseNode.getValue("license").jsonPrimitive.content
        val location = licenseNode.getValue("location").jsonObject

        val path = location.getValue("path").jsonPrimitive.content
        val startLine = location.getValue("start_line").jsonPrimitive.int
        val endLine = location.getValue("end_line").jsonPrimitive.int
        val score = licenseNode.getValue("score").jsonPrimitive.float

        LicenseFinding(license, TextLocation(path, startLine, endLine), score)
    }
}

private fun getCopyrightFindings(result: JsonObject): Set<CopyrightFinding> {
    val copyrights = result["copyrights"]?.jsonArray ?: return emptySet()

    return copyrights.mapTo(mutableSetOf()) {
        val copyrightNode = it.jsonObject

        val statement = copyrightNode.getValue("statement").jsonPrimitive.content
        val location = copyrightNode.getValue("location").jsonObject

        val path = location.getValue("path").jsonPrimitive.content
        val startLine = location.getValue("start_line").jsonPrimitive.int
        val endLine = location.getValue("end_line").jsonPrimitive.int

        CopyrightFinding(statement, TextLocation(path, startLine, endLine))
    }
}

private fun getIssues(result: JsonObject): List<Issue> {
    val issues = result["issues"]?.jsonArray ?: return emptyList()

    return issues.map {
        val issueNode = it.jsonObject
        val timestamp = Instant.parse(issueNode.getValue("timestamp").jsonPrimitive.content)
        val source = issueNode.getValue("source").jsonPrimitive.content
        val message = issueNode.getValue("message").jsonPrimitive.content
        val severity = Severity.valueOf(issueNode.getValue("severity").jsonPrimitive.content.uppercase())

        Issue(timestamp, source, message, severity)
    }
}
