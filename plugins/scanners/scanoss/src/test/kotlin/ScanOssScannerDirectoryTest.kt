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
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should

import io.mockk.every
import io.mockk.spyk
import io.mockk.verify

import java.io.File
import java.util.UUID

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.Snippet
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScannerWrapperConfig
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

private val TEST_DIRECTORY_TO_SCAN = File("src/test/assets/filesToScan")

/**
 * A test for scanning a directory with the [ScanOss] scanner.
 */
class ScanOssScannerDirectoryTest : StringSpec({
    lateinit var scanner: ScanOss

    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderDirectory("src/test/assets/scanMulti")
    )

    beforeSpec {
        server.start()
        val config = ScanOssConfig(apiUrl = "http://localhost:${server.port()}", apiKey = "")
        scanner = spyk(ScanOss.Factory().create(config, ScannerWrapperConfig.EMPTY))
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

        val summary = scanner.scanPath(
            TEST_DIRECTORY_TO_SCAN,
            ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE)
        )

        verify(exactly = 1) {
            scanner.createWfpForFile(TEST_DIRECTORY_TO_SCAN.resolve("ArchiveUtils.kt"))
            scanner.createWfpForFile(TEST_DIRECTORY_TO_SCAN.resolve("ScannerFactory.kt"))
        }

        with(summary) {
            licenseFindings should containExactlyInAnyOrder(
                LicenseFinding(
                    license = "Apache-2.0",
                    location = TextLocation(
                        path = "scanner/src/main/kotlin/ScannerFactory.kt",
                        line = TextLocation.UNKNOWN_LINE
                    ),
                    score = 100.0f
                )
            )

            snippetFindings.shouldContainExactly(
                SnippetFinding(
                    TextLocation("utils/src/main/kotlin/ArchiveUtils.kt", 1, 240),
                    setOf(
                        Snippet(
                            99.0f,
                            TextLocation(
                                "https://osskb.org/api/file_contents/871fb0c5188c2f620d9b997e225b0095",
                                128,
                                367
                            ),
                            RepositoryProvenance(
                                VcsInfo(VcsType.GIT, "https://github.com/scanoss/ort.git", ""), "."
                            ),
                            "pkg:github/scanoss/ort",
                            SpdxExpression.parse("Apache-2.0")
                        )
                    )
                )
            )
        }
    }
})
