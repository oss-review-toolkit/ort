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

const val TEST_FILE_TO_SCAN = "src/test/assets/scanoss/filesToScan/ScannerFactory.kt"

/**
 * A test for scanning a single file with the [ScanOss] scanner.
 */
class ScanOssScannerFileTest : StringSpec({
    lateinit var scanner: ScanOss

    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory("src/test/assets/scanoss/scanSingle")
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

    "The scanner should scan a single file" {
        // Manipulate the UUID generation to have the same IDs as in the response.
        every {
            scanner.generateRandomUUID()
        } answers {
            UUID.fromString("bf5401e9-03b3-4c91-906c-cadb90487b8c")
        }
        val result = scanner.scanPath(File(TEST_FILE_TO_SCAN))

        verify(exactly = 1) {
            scanner.createWfpForFile(TEST_FILE_TO_SCAN)
        }

        result.scanner shouldNotBeNull {
            results.scanResults shouldHaveSize 1
            results.scanResults[results.scanResults.firstKey()] shouldNotBeNull {
                this shouldHaveSize 1
                this.first() shouldNotBeNull {
                    summary.packageVerificationCode shouldBe "027fef3ecf34d1069b3cd60dad127195aeb069be"
                    summary.licenseFindings shouldHaveSize 1
                    summary.licenseFindings.first().license.toString() shouldBe "Apache-2.0"
                }
            }
        }
    }
})
