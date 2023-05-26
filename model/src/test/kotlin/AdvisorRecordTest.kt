/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.should

import java.net.URI
import java.time.Instant

import org.ossreviewtoolkit.utils.common.enumSetOf

class AdvisorRecordTest : WordSpec({
    "collectIssues" should {
        "return a map which does not contain entries for IDs without any issues" {
            val record = advisorRecordOf(
                langId to listOf(createResult())
            )

            val issues = record.getIssues()

            issues.keys shouldNotContain langId
        }

        "return a map with all issues in the record" {
            val issue1 = Issue(source = "Advisor", message = "Failure1")
            val issue2 = Issue(source = "Advisor", message = "Failure2")
            val issue3 = Issue(source = "Advisor", message = "Warning", severity = Severity.WARNING)
            val record = advisorRecordOf(
                langId to listOf(createResult(issues = listOf(issue3))),
                queryId to listOf(createResult(issues = listOf(issue1, issue2)))
            )

            val issues = record.getIssues()

            issues.keys should containExactlyInAnyOrder(langId, queryId)
            issues[langId] should containExactly(issue3)
            issues[queryId] should containExactlyInAnyOrder(issue1, issue2)
        }
    }

    "getVulnerabilities" should {
        "return an empty list for an unknown package" {
            val record = advisorRecordOf()

            record.getVulnerabilities(langId) should beEmpty()
        }

        "return the vulnerabilities of a specific package" {
            val vul1 = createVulnerability("CVE-2021-1")
            val vul2 = createVulnerability("CVE-2021-2")

            val record = advisorRecordOf(
                queryId to listOf(createResult(vulnerabilities = listOf(vul1, vul2)))
            )

            record.getVulnerabilities(queryId) should containExactly(vul1, vul2)
        }

        "combine the vulnerabilities of a specific package from multiple advisor results" {
            val vul1 = createVulnerability("CVE-2021-1")
            val vul2 = createVulnerability("CVE-2021-2")

            val record = advisorRecordOf(
                queryId to listOf(
                    createResult(advisorIndex = 1, vulnerabilities = listOf(vul1)),
                    createResult(advisorIndex = 2, vulnerabilities = listOf(vul2))
                )
            )

            record.getVulnerabilities(queryId) should containExactly(vul1, vul2)
        }

        "merge the references of vulnerabilities" {
            val otherSource = "https://vulnerabilities.example.org/"
            val vul1 = createVulnerability("CVE-2021-1")
            val vul2 = createVulnerability("CVE-2021-1", otherSource, "cvssv2", "7")
            val vul3 = createVulnerability("CVE-2021-2")
            val mergedVulnerability = Vulnerability(id = vul1.id, references = vul1.references + vul2.references)

            val record = advisorRecordOf(
                queryId to listOf(
                    createResult(advisorIndex = 1, vulnerabilities = listOf(vul1)),
                    createResult(advisorIndex = 2, vulnerabilities = listOf(vul2, vul3))
                )
            )

            record.getVulnerabilities(queryId) should containExactly(mergedVulnerability, vul3)
        }

        "remove duplicate references when merging vulnerabilities" {
            val otherSource = "https://vulnerabilities.example.org/"
            val vul1 = createVulnerability("CVE-2021-1")
            val vul2 = createVulnerability("CVE-2021-1", otherSource, "cvssv2", "7")
            val vul3 = createVulnerability("CVE-2021-1")
            val vul4 = createVulnerability("CVE-2021-1", otherSource, "cvssv3", "5")
            val mergedVulnerability = Vulnerability(
                id = vul1.id,
                references = vul1.references + vul2.references + vul4.references
            )

            val record = advisorRecordOf(
                queryId to listOf(
                    createResult(advisorIndex = 1, vulnerabilities = listOf(vul1)),
                    createResult(advisorIndex = 2, vulnerabilities = listOf(vul2, vul3, vul4))
                )
            )

            record.getVulnerabilities(queryId) should containExactly(mergedVulnerability)
        }
    }

    "getDefects" should {
        "return an empty list for an unknown package" {
            val record = advisorRecordOf()

            record.getDefects(langId) should beEmpty()
        }

        "return the combined defects detected for a specific package" {
            val defect1 = createDefect("d1")
            val defect2 = createDefect("d2")
            val defect3 = createDefect("d3")
            val defect4 = createDefect("d4")
            val defect5 = createDefect("d5")

            val record = advisorRecordOf(
                queryId to listOf(
                    createResult(advisorIndex = 1, defects = listOf(defect1, defect2)),
                    createResult(advisorIndex = 2, defects = listOf(defect3, defect4, defect5))
                )
            )

            record.getDefects(queryId) should containExactlyInAnyOrder(defect1, defect2, defect3, defect4, defect5)
        }
    }

    "filterResults" should {
        "return only results matching a filter" {
            val matchingResult = createResult(
                advisorIndex = 1,
                vulnerabilities = listOf(createVulnerability("CVE-1")),
                defects = listOf(createDefect("bug1"))
            )

            val record = advisorRecordOf(
                queryId to listOf(createResult(advisorIndex = 1), createResult(advisorIndex = 2)),
                langId to listOf(matchingResult, createResult(advisorIndex = 2))
            )

            val filteredResults = record.filterResults { it == matchingResult }

            filteredResults.keys should containExactly(langId)
            filteredResults.getValue(langId) should containExactly(matchingResult)
        }

        "provide a predefined filter for results with vulnerabilities" {
            val result1 = createResult(advisorIndex = 1, vulnerabilities = listOf(createVulnerability("CVE-1")))
            val result2 = createResult(advisorIndex = 2, defects = listOf(createDefect("BUG-1")))
            val result3 = createResult(advisorIndex = 3)
            val result4 = createResult(advisorIndex = 4, vulnerabilities = listOf(createVulnerability("CVE-2")))

            val record = advisorRecordOf(
                queryId to listOf(result1, result2, result3, result4)
            )

            val filteredResults = record.filterResults(AdvisorRecord.RESULTS_WITH_VULNERABILITIES)

            filteredResults.keys should containExactly(queryId)
            filteredResults.getValue(queryId) should containExactlyInAnyOrder(result1, result4)
        }

        "provide a predefined filter for results with defects" {
            val result1 = createResult(advisorIndex = 1, vulnerabilities = listOf(createVulnerability("CVE-1")))
            val result2 = createResult(advisorIndex = 2, defects = listOf(createDefect("BUG-1")))
            val result3 = createResult(advisorIndex = 3)
            val result4 = createResult(advisorIndex = 4, vulnerabilities = listOf(createVulnerability("CVE-2")))

            val record = advisorRecordOf(
                queryId to listOf(result1, result2, result3, result4)
            )

            val filteredResults = record.filterResults(AdvisorRecord.RESULTS_WITH_DEFECTS)

            filteredResults.keys should containExactly(queryId)
            filteredResults.getValue(queryId) should containExactly(result2)
        }

        "provide a predefined filter for results with issues" {
            val result1 = createResult(
                advisorIndex = 1,
                issues = listOf(Issue(source = "test", message = "test message", severity = Severity.HINT))
            )
            val result2 = createResult(advisorIndex = 2)

            val record = advisorRecordOf(
                queryId to listOf(result1),
                langId to listOf(result2)
            )

            val filteredResults = record.filterResults(AdvisorRecord.resultsWithIssues())

            filteredResults.keys should containExactly(queryId)
            filteredResults.getValue(queryId) should containExactly(result1)
        }

        "provide a predefined filter for results with issues that have a minimum severity" {
            val result1 = createResult(
                advisorIndex = 1,
                issues = listOf(Issue(source = "test", message = "test message", severity = Severity.ERROR))
            )
            val result2 = createResult(
                advisorIndex = 2,
                issues = listOf(Issue(source = "test", message = "test message", severity = Severity.WARNING)),
                capability = AdvisorCapability.DEFECTS
            )

            val record = advisorRecordOf(
                queryId to listOf(result1),
                langId to listOf(result2)
            )

            val filteredResults = record.filterResults(AdvisorRecord.resultsWithIssues(minSeverity = Severity.ERROR))

            filteredResults.keys should containExactly(queryId)
            filteredResults.getValue(queryId) should containExactly(result1)
        }

        "provide a predefined filter for results with issues for a given advisor capability" {
            val result1 = createResult(
                advisorIndex = 1,
                issues = listOf(Issue(source = "test", message = "test message", severity = Severity.ERROR))
            )
            val result2 = createResult(
                advisorIndex = 2,
                issues = listOf(Issue(source = "test", message = "test message", severity = Severity.WARNING)),
                capability = AdvisorCapability.DEFECTS
            )

            val record = advisorRecordOf(
                queryId to listOf(result1), langId to listOf(result2)
            )

            val filteredResults = record.filterResults(
                AdvisorRecord.resultsWithIssues(
                    minSeverity = Severity.WARNING,
                    capability = AdvisorCapability.VULNERABILITIES
                )
            )

            filteredResults.keys should containExactly(queryId)
            filteredResults.getValue(queryId) should containExactly(result1)
        }
    }
})

/** The prefix for URIs pointing to the source of vulnerabilities. */
private const val SOURCE_URI_PREFIX = "http://cve.mitre.org/cgi-bin/cvename.cgi?name="

/** A scoring system used by vulnerabilities. */
private const val SCORING_SYSTEM = "cvssv3.1_qr"

/** The default severity assigned to vulnerabilities. */
private const val DEFAULT_SEVERITY = "MODERATE"

/** Test package identifiers. */
private val langId = Identifier("Maven", "org.apache.commons", "commons-lang3", "3.8")
private val queryId = Identifier("NPM", "", "jQuery", "2.1.4")

/**
 * Construct a [Vulnerability] with the given [id] that has a single [VulnerabilityReference] pointing to a source
 * URI derived from the given [uriPrefix] with the [scoringSystem] and [severity] provided.
 */
private fun createVulnerability(
    id: String,
    uriPrefix: String = SOURCE_URI_PREFIX,
    scoringSystem: String = SCORING_SYSTEM,
    severity: String = DEFAULT_SEVERITY
): Vulnerability =
    Vulnerability(
        id = id,
        references = listOf(
            VulnerabilityReference(URI("$uriPrefix$id"), scoringSystem, severity)
        )
    )

/**
 * Construct a [Defect] based on the given [id].
 */
private fun createDefect(id: String): Defect =
    Defect(id, URI("https://defects.example.org/$id"), "Defect $id")

/**
 * Create an [AdvisorResult] for an advisor with the given [advisorIndex] which has the given [capability], with the
 * passed in [issues], [vulnerabilities], and [defects].
 */
private fun createResult(
    advisorIndex: Int = 1,
    issues: List<Issue> = emptyList(),
    vulnerabilities: List<Vulnerability> = emptyList(),
    defects: List<Defect> = emptyList(),
    capability: AdvisorCapability = AdvisorCapability.VULNERABILITIES
): AdvisorResult {
    val details = AdvisorDetails("advisor$advisorIndex", enumSetOf(capability))
    val summary = AdvisorSummary(
        startTime = Instant.parse("2021-04-06T13:26:05.123Z"),
        endTime = Instant.parse("2021-04-06T13:26:47.456Z"),
        issues = issues
    )

    return AdvisorResult(details, summary, defects, vulnerabilities)
}

private fun advisorRecordOf(vararg results: Pair<Identifier, List<AdvisorResult>>) = AdvisorRecord(results.toMap())
