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

import java.time.Instant

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation

internal fun generateSummary(startTime: Instant, endTime: Instant, jsonString: String): ScanSummary {
    val mapper = ObjectMapper()
    val result: JsonNode = mapper.readTree(jsonString)
    val licenseFindings = getLicenseFindings(result)
    val copyrightFindings = getCopyrightFindings(result)

    return ScanSummary(
        startTime,
        endTime,
        licenseFindings,
        copyrightFindings
    )
}
private fun getLicenseFindings(result: JsonNode): Set<LicenseFinding> {
    val licenseFindings = mutableListOf<LicenseFinding>()
    val licenses = result["licenses"] ?: return emptySet()

    licenses.forEach {
        val license = it["license"].asText()
        val location = it["location"]
        val path = location["path"].asText()
        val startLine = location["start_line"].asInt()
        val endLine = location["end_line"].asInt()
        val score = it["score"].asDouble()
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

    copyrights.forEach {
        val statement = it["statement"].asText()
        val location = it["location"]
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
