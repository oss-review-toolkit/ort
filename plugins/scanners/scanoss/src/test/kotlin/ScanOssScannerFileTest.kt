/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.scanoss

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should

import io.mockk.spyk

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.plugins.api.Secret
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.utils.common.extractResource

/**
 * A test for scanning a single file with the [ScanOss] scanner.
 */
class ScanOssScannerFileTest : StringSpec({
    lateinit var scanner: ScanOss

    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("scanSingle")
    )

    beforeSpec {
        server.start()
        scanner = spyk(ScanOssFactory.create(apiUrl = "http://localhost:${server.port()}", apiKey = Secret("")))
    }

    afterSpec {
        server.stop()
    }

    beforeTest {
        server.resetAll()
    }

    "The scanner should scan a single file" {
        val pathToFile = extractResource("/filesToScan/random-data-05-07-04.kt", tempfile())
        val summary = scanner.scanPath(
            pathToFile,
            ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE)
        )

        with(summary) {
            licenseFindings should containExactly(
                LicenseFinding(
                    license = "Apache-2.0",
                    location = TextLocation(
                        path = "scanner/src/main/kotlin/random-data-05-07-04.kt",
                        line = TextLocation.UNKNOWN_LINE
                    ),
                    score = 100.0f
                )
            )
        }
    }
})
