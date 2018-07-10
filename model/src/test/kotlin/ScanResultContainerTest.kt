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

    private val scanSummary1 = ScanSummary(
            scannerStartTime1,
            scannerEndTime1,
            1,
            sortedSetOf("license 1.1", "license 1.2"),
            mutableListOf("error 1.1", "error 1.2")
    )
    private val scanSummary2 = ScanSummary(
            scannerStartTime2,
            scannerEndTime2,
            2,
            sortedSetOf("license 2.1", "license 2.2"),
            mutableListOf("error 2.1", "error 2.2")
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
                val deserializedScanResults =
                        jsonMapper.readValue(serializedScanResults, ScanResultContainer::class.java)

                deserializedScanResults shouldBe scanResults
            }

            "serialize as expected" {
                val expectedScanResultsFile = File("src/test/assets/expected-scan-results.yml")
                val expectedScanResults = expectedScanResultsFile.readText()

                val serializedScanResults = yamlMapper.writeValueAsString(scanResults)

                serializedScanResults shouldBe expectedScanResults
            }
        }
    }
}
