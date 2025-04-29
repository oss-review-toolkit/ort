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

package org.ossreviewtoolkit.clients.fossid

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.file.shouldHaveName
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.sequences.shouldContain
import io.kotest.matchers.string.shouldEndWith

import java.io.File

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.clients.fossid.model.report.ReportType
import org.ossreviewtoolkit.clients.fossid.model.report.SelectionType

private const val SCAN_CODE = "semver4j_semver4j__20220119_094101_delta"

class FossIdReportTest : StringSpec({
    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("report")
    )
    lateinit var service: FossIdRestService
    lateinit var directory: File

    beforeSpec {
        server.start()
        service = FossIdRestService.create("http://localhost:${server.port()}")

        directory = createTempDirectory("fossid_report").toFile()
    }

    afterSpec {
        server.stop()
        directory.delete()
    }

    beforeTest {
        server.resetAll()
    }

    "A dynamic HTML report can be generated for a scan" {
        val result = service.generateReport(
            "", "", SCAN_CODE, ReportType.HTML_DYNAMIC, SelectionType.INCLUDE_FOSS, directory
        )
        result shouldBeSuccess {
            it shouldHaveName "fossid-" +
                "semver4j_semver4j__20220119_094101_delta-" +
                "cc1267688905d7493df292786a245297c9fd36ee.html"
            it.useLines { lines ->
                lines shouldContain "<html>"
            }
        }
    }

    "An Excel sheet can be generated for a scan" {
        val result = service.generateReport(
            "", "", SCAN_CODE, ReportType.XLSX, SelectionType.INCLUDE_FOSS, directory
        )

        result shouldBeSuccess {
            it shouldHaveName "semver4j_semver4j__20220119_094101_delta-4d39359cd861c314020ead401d95ca20dc179495.xlsx"
        }
    }

    "A HTML report can be generated for a scan" {
        val result = service.generateReport(
            "", "", SCAN_CODE, ReportType.HTML_STATIC, SelectionType.INCLUDE_FOSS, directory
        )

        result shouldBeSuccess {
            it.name shouldEndWith "$SCAN_CODE-report.html"
            it.useLines { lines ->
                lines shouldContain "<html>"
            }
        }
    }
})
