/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package com.here.ort.scanner

import com.here.ort.model.LicenseFindings
import com.here.ort.model.Provenance
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.ScannerDetails
import com.here.ort.model.config.ArtifactoryStorageConfiguration
import com.here.ort.scanner.storages.ArtifactoryStorage

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec

import java.time.Instant

@Suppress("UnsafeCallOnNullableType", "UnsafeCast")
class ScanResultsStorageTest : WordSpec() {
    private fun ArtifactoryStorage.getStringField(name: String): String {
        javaClass.getDeclaredField(name).let {
            it.isAccessible = true
            return it.get(this) as String
        }
    }

    private fun ArtifactoryStorage.getApiToken() = getStringField("apiToken")

    private fun ArtifactoryStorage.getRepository() = getStringField("repository")

    private fun ArtifactoryStorage.getUrl() = getStringField("url")

    init {
        "ScanResultsStorage.configure" should {
            "fail if the Artifactory URL is empty" {
                val exception = shouldThrow<IllegalArgumentException> {
                    val config = ArtifactoryStorageConfiguration("", "someRepository", "someApiToken")

                    ScanResultsStorage.configure(config)
                }
                exception.message shouldBe "URL for Artifactory storage is missing."
            }

            "fail if the Artifactory repository is empty" {
                val exception = shouldThrow<java.lang.IllegalArgumentException> {
                    val config = ArtifactoryStorageConfiguration("someUrl", "", "someApiToken")

                    ScanResultsStorage.configure(config)
                }
                exception.message shouldBe "Repository for Artifactory storage is missing."
            }

            "fail if the Artifactory apiToken is empty" {
                val exception = shouldThrow<IllegalArgumentException> {
                    val config = ArtifactoryStorageConfiguration("someUrl", "someRepository", "")

                    ScanResultsStorage.configure(config)
                }
                exception.message shouldBe "API token for Artifactory storage is missing."
            }

            "configure the Artifactory storage correctly" {
                val config = ArtifactoryStorageConfiguration("someUrl", "someRepository", "someApiToken")

                ScanResultsStorage.configure(config)

                ScanResultsStorage.storage shouldNotBe null
                ScanResultsStorage.storage::class shouldBe ArtifactoryStorage::class
                (ScanResultsStorage.storage as ArtifactoryStorage).apply {
                    getUrl() shouldBe "someUrl"
                    getRepository() shouldBe "someRepository"
                    getApiToken() shouldBe "someApiToken"
                }
            }
        }

        "patchScanCodeLicenseRefs" should {
            "correctly patch existing ScanCode LicenseRef findings" {
                fun generateDummyLicenseFinding(license: String) =
                    LicenseFindings(license, sortedSetOf(), sortedSetOf())

                val originalScanResult = ScanResult(
                    Provenance(),
                    ScannerDetails("ScanCode", "", ""),
                    ScanSummary(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        0,
                        sortedSetOf(
                            generateDummyLicenseFinding("LicenseRef-foo-bar"),
                            generateDummyLicenseFinding("LicenseRef-scancode-correct"),
                            generateDummyLicenseFinding("Apache-2.0")
                        )
                    )
                )

                val expectedScanResult = ScanResult(
                    Provenance(),
                    ScannerDetails("ScanCode", "", ""),
                    ScanSummary(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        0,
                        sortedSetOf(
                            generateDummyLicenseFinding("LicenseRef-scancode-foo-bar"),
                            generateDummyLicenseFinding("LicenseRef-scancode-correct"),
                            generateDummyLicenseFinding("Apache-2.0")
                        )
                    )
                )

                (ScanResultsStorage.storage as ArtifactoryStorage).apply {
                    val actualScanResults = patchScanCodeLicenseRefs(listOf(originalScanResult)).single()
                    actualScanResults shouldBe expectedScanResult
                }
            }
        }
    }
}
