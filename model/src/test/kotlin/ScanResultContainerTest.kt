/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File
import java.time.Duration
import java.time.Instant

import org.ossreviewtoolkit.utils.test.patchActualResult

class ScanResultContainerTest : WordSpec() {
    private val id = Identifier("type", "namespace", "name", "version")

    private val downloadTime1 = Instant.EPOCH + Duration.ofDays(1)
    private val downloadTime2 = Instant.EPOCH + Duration.ofDays(2)

    private val provenance1 = ArtifactProvenance(
        sourceArtifact = RemoteArtifact("url", Hash.create("hash"))
    )
    private val provenance2 = RepositoryProvenance(
        vcsInfo = VcsInfo(VcsType("type"), "url", "revision", "path"),
        resolvedRevision = "resolvedRevision"
    )

    private val scannerDetails1 = ScannerDetails("name 1", "version 1", "config 1")
    private val scannerDetails2 = ScannerDetails("name 2", "version 2", "config 2")

    private val scannerStartTime1 = downloadTime1 + Duration.ofMinutes(1)
    private val scannerEndTime1 = scannerStartTime1 + Duration.ofMinutes(1)
    private val scannerStartTime2 = downloadTime2 + Duration.ofMinutes(1)
    private val scannerEndTime2 = scannerStartTime2 + Duration.ofMinutes(1)

    private val issue11 = OrtIssue(source = "source-11", message = "issue-11")
    private val issue12 = OrtIssue(source = "source-12", message = "issue-12")
    private val issue21 = OrtIssue(source = "source-21", message = "issue-21")
    private val issue22 = OrtIssue(source = "source-22", message = "issue-22")

    private val scanSummary1 = ScanSummary(
        scannerStartTime1,
        scannerEndTime1,
        "packageVerificationCode",
        sortedSetOf(
            LicenseFinding(
                "license-1.1",
                TextLocation("path 1.1", 1)
            ),
            LicenseFinding(
                "license-1.2",
                TextLocation("path 1.2", 1, 2)
            )
        ),
        sortedSetOf(
            CopyrightFinding(
                "copyright 1",
                TextLocation("copyright path 1.1", 1)
            ),
            CopyrightFinding(
                "copyright 2",
                TextLocation("copyright path 1.2", 1, 2)
            )
        ),
        mutableListOf(issue11, issue12)
    )
    private val scanSummary2 = ScanSummary(
        scannerStartTime2,
        scannerEndTime2,
        "packageVerificationCode",
        sortedSetOf(
            LicenseFinding(
                "license-2.1",
                TextLocation("path/to/file", 1, 2)

            ),
            LicenseFinding(
                "license-2.2",
                TextLocation("path/to/another/file", 3, 4)
            )
        ),
        sortedSetOf(
            CopyrightFinding("copyright 3", TextLocation("path/to/file", 1, 2)),
            CopyrightFinding("copyright 4", TextLocation("path/to/another/file", 3, 4))
        ),
        mutableListOf(issue21, issue22)
    )

    private val scanResult1 = ScanResult(provenance1, scannerDetails1, scanSummary1)
    private val scanResult2 = ScanResult(provenance2, scannerDetails2, scanSummary2)

    private val scanResults = ScanResultContainer(id, listOf(scanResult1, scanResult2))

    init {
        "ScanResults" should {
            "be serialized and deserialized correctly" {
                val serializedScanResults = jsonMapper.writeValueAsString(scanResults)
                val deserializedScanResults = jsonMapper.readValue<ScanResultContainer>(serializedScanResults)

                deserializedScanResults shouldBe scanResults
            }

            "serialize as expected" {
                val expectedScanResultsFile = File("src/test/assets/expected-scan-results.yml")
                val expectedScanResults = expectedScanResultsFile.readText()

                val serializedScanResults = yamlMapper.writeValueAsString(scanResults)

                patchActualResult(serializedScanResults) shouldBe expectedScanResults
            }
        }
    }
}
