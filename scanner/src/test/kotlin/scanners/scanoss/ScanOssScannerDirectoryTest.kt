/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners.scanoss

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.spyk
import io.mockk.verify

import java.io.File
import java.util.UUID

import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

const val TEST_DIRECTORY_TO_SCAN = "src/test/assets/scanoss/filesToScan"

/**
 * A test for scanning a directory with the [ScanOss] scanner.
 */
class ScanOssScannerDirectoryTest : StringSpec({
    lateinit var scanner: ScanOss

    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory("src/test/assets/scanoss/scanMulti")
    )

    beforeSpec {
        server.start()
        val scannerOptions = mapOf(ScanOssConfig.API_URL_PROPERTY to "http://localhost:${server.port()}")
        val configuration = ScannerConfiguration(options = mapOf("ScanOss" to scannerOptions))
        scanner = spyk(ScanOss.Factory().create(configuration, DownloaderConfiguration()))
    }

    afterSpec {
        server.stop()
    }

    beforeTest {
        server.resetAll()
    }

    "The scanner should scan a directory" {
        // Manipulate the UUID generation to have the same IDs as in the response.
        every {
            scanner.generateRandomUUID()
        } answers {
            UUID.fromString("5530105e-0752-4750-9c07-4e4604b879a5")
        } andThenAnswer {
            UUID.fromString("c198b884-f6cf-496f-95eb-0e7968dd2ec6")
        }
        val result = scanner.scanPath(File(TEST_DIRECTORY_TO_SCAN))

        verify(exactly = 1) {
            scanner.createWfpForFile("$TEST_DIRECTORY_TO_SCAN/ArchiveUtils.kt")
            scanner.createWfpForFile("$TEST_DIRECTORY_TO_SCAN/ScannerFactory.kt")
        }

        result.scanner shouldNotBeNull {
            results.scanResults shouldHaveSize 1
            results.scanResults[results.scanResults.firstKey()] shouldNotBeNull {
                this shouldHaveSize 1
                this.first() shouldNotBeNull {
                    summary.packageVerificationCode shouldBe "07c881ae4fcc30a69f5d66453d54d194f062252e"
                    summary.licenseFindings shouldHaveSize 2
                    summary.licenseFindings.first().license.toString() shouldBe "Apache-2.0"
                    summary.licenseFindings.last().license.toString() shouldBe "Apache-2.0"
                }
            }
        }
    }
})
