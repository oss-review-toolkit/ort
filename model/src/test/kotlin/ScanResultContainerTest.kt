/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.fasterxml.jackson.module.kotlin.readValue

import com.here.ort.utils.test.patchActualResult

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File
import java.time.Duration
import java.time.Instant

class ScanResultContainerTest : WordSpec() {
    private val id = Identifier("provider", "namespace", "name", "version")

    private val downloadTime1 = Instant.EPOCH + Duration.ofDays(1)
    private val downloadTime2 = Instant.EPOCH + Duration.ofDays(2)

    private val provenance1 = Provenance(
            downloadTime = downloadTime1,
            sourceArtifact = RemoteArtifact("url", "hash", HashAlgorithm.SHA1)
    )
    private val provenance2 = Provenance(
            downloadTime = downloadTime2,
            vcsInfo = VcsInfo("type", "url", "revision", "resolvedRevision", "path")
    )

    private val scannerDetails1 = ScannerDetails("name 1", "version 1", "config 1")
    private val scannerDetails2 = ScannerDetails("name 2", "version 2", "config 2")

    private val scannerStartTime1 = downloadTime1 + Duration.ofMinutes(1)
    private val scannerEndTime1 = scannerStartTime1 + Duration.ofMinutes(1)
    private val scannerStartTime2 = downloadTime2 + Duration.ofMinutes(1)
    private val scannerEndTime2 = scannerStartTime2 + Duration.ofMinutes(1)

    private val error11 = Error(source = "source-11", message = "error-11")
    private val error12 = Error(source = "source-12", message = "error-12")
    private val error21 = Error(source = "source-21", message = "error-21")
    private val error22 = Error(source = "source-22", message = "error-22")

    private val scanSummary1 = ScanSummary(
            scannerStartTime1,
            scannerEndTime1,
            1,
            sortedSetOf(
                    LicenseFinding("license 1.1", sortedSetOf("copyright 1")),
                    LicenseFinding("license 1.2", sortedSetOf("copyright 2")))
            ,
            mutableListOf(error11, error12)
    )
    private val scanSummary2 = ScanSummary(
            scannerStartTime2,
            scannerEndTime2,
            2,
            sortedSetOf(
                    LicenseFinding("license 2.1", sortedSetOf("copyright 3")),
                    LicenseFinding("license 2.2", sortedSetOf("copyright 4"))
            ),
            mutableListOf(error21, error22)
    )

    private val rawResult1 = jsonMapper.readTree("\"key 1\": \"value 1\"")
    private val rawResult2 = jsonMapper.readTree("\"key 2\": \"value 2\"")

    private val scanResult1 =
            ScanResult(provenance1, scannerDetails1, scanSummary1, rawResult1)
    private val scanResult2 =
            ScanResult(provenance2, scannerDetails2, scanSummary2, rawResult2)

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

            "deprecated licenses field in scan summary can be parsed" {
                val deprecatedScanResultsFile = File("src/test/assets/deprecated-licenses-scan-results.yml")
                val scanResults = deprecatedScanResultsFile.readValue(ScanResultContainer::class.java)

                scanResults.results[0].summary.licenses shouldBe sortedSetOf("license 1.1", "license 1.2")
                scanResults.results[1].summary.licenses shouldBe sortedSetOf("license 2.1", "license 2.2")
            }

            "deprecated errors field in scan summary can be parsed" {
                val deprecatedScanResultsFile = File("src/test/assets/deprecated-errors-scan-results.yml")
                val scanResults = deprecatedScanResultsFile.readValue(ScanResultContainer::class.java)

                scanResults.results[0].summary.errors shouldBe listOf(
                        Error(timestamp = Instant.EPOCH, source = "", message = "error-11"),
                        Error(timestamp = Instant.EPOCH, source = "", message = "error-12")
                )

                scanResults.results[1].summary.errors shouldBe listOf(
                        Error(timestamp = Instant.EPOCH, source = "", message = "error-21"),
                        Error(timestamp = Instant.EPOCH, source = "", message = "error-22")
                )
            }
        }
    }
}
