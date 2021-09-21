/*
 * Copyright (C) 2021 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.net.URI
import java.time.Instant

class AdvisorRecordTest : WordSpec({
    "Deserialization" should {
        "work for a map of advisor results" {
            val yaml = """
            ---
            advisor_results:
              type:namespace:name:version:
              - findings: []
                advisor:
                  name: "advisor"
                summary:
                  start_time: "1970-01-01T00:00:00Z"
                  end_time: "1970-01-01T00:00:00Z"
            has_issues: false
        """.trimIndent()

            val record = shouldNotThrowAny { yamlMapper.readValue<AdvisorRecord>(yaml) }
            record.advisorResults should haveSize(1)
            record.advisorResults.entries.single().let { (id, results) ->
                id shouldBe Identifier("type:namespace:name:version")
                results shouldHaveSize 1
            }
        }

        "work for a legacy AdvisorRecord with AdvisorResultContainers" {
            val yaml = """
            ---
            advisor_results:
            - id: "type:namespace:name:version"
              results:
              - vulnerabilities: []
                advisor:
                  name: "advisor"
                summary:
                  start_time: "1970-01-01T00:00:00Z"
                  end_time: "1970-01-01T00:00:00Z"
            has_issues: false
        """.trimIndent()

            val record = shouldNotThrowAny { yamlMapper.readValue<AdvisorRecord>(yaml) }
            record.advisorResults should haveSize(1)
            record.advisorResults.entries.single().let { (id, results) ->
                id shouldBe Identifier("type:namespace:name:version")
                results shouldHaveSize 1
            }
        }
    }

    "hasIssues" should {
        "return false given the record has no issues" {
            val vul1 = createFinding("CVE-2021-1")
            val vul2 = createFinding("CVE-2021-2")
            val record = AdvisorRecord(
                sortedMapOf(
                    langId to listOf(createResult(1)),
                    queryId to listOf(createResult(1, findings = listOf(vul1, vul2)))
                )
            )

            record.hasIssues shouldBe false
        }

        "return true given the record has issues" {
            val vul1 = createFinding("CVE-2021-1")
            val vul2 = createFinding("CVE-2021-2")
            val record = AdvisorRecord(
                sortedMapOf(
                    langId to listOf(createResult(1)),
                    queryId to listOf(
                        createResult(1, findings = listOf(vul1)),
                        createResult(
                            2,
                            issues = listOf(OrtIssue(source = "Advisor", message = "Failure")),
                            findings = listOf(vul2)
                        )
                    )
                )
            )

            record.hasIssues shouldBe true
        }
    }

    "collectIssues" should {
        "return a map with empty sets if no issues are present" {
            val vul1 = createFinding("CVE-2021-1")
            val vul2 = createFinding("CVE-2021-2")
            val record = AdvisorRecord(
                sortedMapOf(
                    langId to listOf(createResult(1)),
                    queryId to listOf(createResult(1, findings = listOf(vul1, vul2)))
                )
            )

            val issues = record.collectIssues()

            issues.keys should containExactlyInAnyOrder(langId, queryId)
            issues.values.forAll { it should beEmpty() }
        }

        "return a map with all issues in the record" {
            val issue1 = OrtIssue(source = "Advisor", message = "Failure1")
            val issue2 = OrtIssue(source = "Advisor", message = "Failure2")
            val issue3 = OrtIssue(source = "Advisor", message = "Warning", severity = Severity.WARNING)
            val vul1 = createFinding("CVE-2021-1")
            val vul2 = createFinding("CVE-2021-2")
            val record = AdvisorRecord(
                sortedMapOf(
                    langId to listOf(createResult(1, issues = listOf(issue3))),
                    queryId to listOf(
                        createResult(
                            1,
                            findings = listOf(vul1, vul2),
                            issues = listOf(issue1, issue2)
                        )
                    )
                )
            )

            val issues = record.collectIssues()

            issues.keys should containExactlyInAnyOrder(langId, queryId)
            issues[langId] should containExactly(issue3)
            issues[queryId] should containExactlyInAnyOrder(issue1, issue2)
        }
    }

    "getFindings" should {
        "return an empty list for an unknown package" {
            val record = AdvisorRecord(sortedMapOf())

            record.getFindings(langId) should beEmpty()
        }

        "return the findings of a specific package" {
            val vul1 = createFinding("CVE-2021-1")
            val vul2 = createFinding("CVE-2021-2")

            val record = AdvisorRecord(
                sortedMapOf(queryId to listOf(createResult(1, findings = listOf(vul1, vul2))))
            )

            record.getFindings(queryId) should containExactly(vul1, vul2)
        }

        "combine the findings of a specific package from multiple advisor results" {
            val vul1 = createFinding("CVE-2021-1")
            val vul2 = createFinding("CVE-2021-2")

            val record = AdvisorRecord(
                sortedMapOf(
                    queryId to listOf(
                        createResult(1, findings = listOf(vul1)),
                        createResult(2, findings = listOf(vul2))
                    )
                )
            )

            record.getFindings(queryId) should containExactly(vul1, vul2)
        }

        "merge the details of findings" {
            val otherSource = "https://vulnerabilities.example.org/"
            val vul1 = createFinding("CVE-2021-1")
            val vul2 = createFinding("CVE-2021-1", otherSource, "cvssv2", "7")
            val vul3 = createFinding("CVE-2021-2")
            val mergedFinding = Finding(vul1.id, vul1.references + vul2.references)

            val record = AdvisorRecord(
                sortedMapOf(
                    queryId to listOf(
                        createResult(1, findings = listOf(vul1)),
                        createResult(2, findings = listOf(vul2, vul3))
                    )
                )
            )

            record.getFindings(queryId) should containExactly(mergedFinding, vul3)
        }

        "remove duplicate details when merging findings" {
            val otherSource = "https://vulnerabilities.example.org/"
            val vul1 = createFinding("CVE-2021-1")
            val vul2 = createFinding("CVE-2021-1", otherSource, "cvssv2", "7")
            val vul3 = createFinding("CVE-2021-1")
            val vul4 = createFinding("CVE-2021-1", otherSource, "cvssv3", "5")
            val mergedFinding = Finding(vul1.id, vul1.references + vul2.references + vul4.references)

            val record = AdvisorRecord(
                sortedMapOf(
                    queryId to listOf(
                        createResult(1, findings = listOf(vul1)),
                        createResult(2, findings = listOf(vul2, vul3, vul4))
                    )
                )
            )

            record.getFindings(queryId) should containExactly(mergedFinding)
        }
    }
})

/** The prefix for URIs pointing to the source of findings. */
private const val SOURCE_URI_PREFIX = "http://cve.mitre.org/cgi-bin/cvename.cgi?name="

/** A scoring system used by findings. */
private const val SCORING_SYSTEM = "cvssv3.1_qr"

/** The default severity assigned to findings. */
private const val DEFAULT_SEVERITY = "MODERATE"

/** Test package identifiers. */
private val langId = Identifier("Maven", "org.apache.commons", "commons-lang3", "3.8")
private val queryId = Identifier("NPM", "", "jQuery", "2.1.4")

/**
 * Construct a [Finding] with the given [id] that has a single [FindingDetail] pointing to a source
 * URI derived from the given [uriPrefix] with the [scoringSystem] and [severity] provided.
 */
private fun createFinding(
    id: String,
    uriPrefix: String = SOURCE_URI_PREFIX,
    scoringSystem: String = SCORING_SYSTEM,
    severity: String = DEFAULT_SEVERITY
): Finding =
    Finding(
        id = id,
        references = listOf(
            FindingDetail(URI("$uriPrefix$id"), scoringSystem, severity)
        )
    )

/**
 * Create an [AdvisorResult] for an advisor with the given [advisorIndex] with the passed in [issues] and
 * [findings].
 */
private fun createResult(
    advisorIndex: Int,
    issues: List<OrtIssue> = emptyList(),
    findings: List<Finding> = emptyList()
): AdvisorResult {
    val details = AdvisorDetails("advisor$advisorIndex")
    val summary = AdvisorSummary(
        startTime = Instant.parse("2021-04-06T13:26:05.123Z"),
        endTime = Instant.parse("2021-04-06T13:26:47.456Z"),
        issues = issues
    )

    return AdvisorResult(findings, details, summary)
}
