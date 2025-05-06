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
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.spyk

import java.io.File

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.Snippet
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.plugins.api.Secret
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

// Define separate directories for different test scenarios.
private val TEST_DIRECTORY_TO_SCAN = File("src/test/assets/filesToScan")
private val EXCLUSION_TEST_DIRECTORY = File("src/test/assets/exclusionTest")

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
        scanner = spyk(ScanOssFactory.create(apiUrl = "http://localhost:${server.port()}", apiKey = Secret("")))
    }

    afterSpec {
        server.stop()
    }

    beforeTest {
        server.resetAll()
    }

    "The scanner should scan a directory" {
        val summary = scanner.scanPath(
            TEST_DIRECTORY_TO_SCAN,
            ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE)
        )

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

            snippetFindings should containExactly(
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

    "Scanner should exclude only files matching the specified path pattern (**/*.kt)" {
        val pathExcludes = listOf(
            PathExclude(
                pattern = "**/*.kt", // Glob pattern to match all .kt files in any directory.
                reason = PathExcludeReason.BUILD_TOOL_OF,
                comment = "Excluding .kt source files from scanning"
            )
        )

        // Verify our test file exists. This file should be included in the scan since it does not match the exclusion
        // pattern (it is a .go file, not a .kt file).
        val includedFile = File(EXCLUSION_TEST_DIRECTORY, "server.go")
        if (!includedFile.isFile) {
            fail("The file ${includedFile.absolutePath} does not exist - test environment may not be properly set up")
        }

        // Run the scanner with our exclusion pattern. This will traverse the directory and should skip .kt files.
        scanner.scanPath(
            EXCLUSION_TEST_DIRECTORY,
            ScanContext(
                labels = emptyMap(),
                packageType = PackageType.PACKAGE,
                excludes = Excludes(paths = pathExcludes)
            )
        )

        // Retrieve all HTTP POST requests captured by WireMock during the scan.
        val requests = server.findAll(WireMock.postRequestedFor(WireMock.anyUrl()))
        val requestBodies = requests.map { it.bodyAsString }

        // The scanner sends files to the API in a multipart/form-data POST request with this format:
        // --boundary
        // Content-Disposition: form-data; name="file"; filename="[UUID].wfp"
        // Content-Type: text/plain; charset=utf-8
        // Content-Length: [length]
        //
        // file=[hash],[size],[filename]
        // [fingerprint data for the file]
        // --boundary--

        // Extract included filenames using a regex pattern from the ScanOSS HTTP POST.
        // The pattern matches lines starting with "file=" followed by hash and size, then captures the filename.
        val filenamePattern = "file=.*?,.*?,(.+)".toRegex(RegexOption.MULTILINE)
        val includedFiles = requestBodies.flatMap { body ->
            filenamePattern.findAll(body).map { it.groupValues[1] }.toList()
        }

        // Verify that .kt files were excluded from the scan.
        // These assertions check that Kotlin files are not present in the API requests.
        includedFiles.any { it.contains("ArchiveUtils.kt") } shouldBe false
        includedFiles.any { it.contains("ScannerFactory.kt") } shouldBe false

        // Verify that non-.kt files were included in the scan.
        // This assertion checks that our Go file was sent to the API.
        includedFiles.any { it.contains("server.go") } shouldBe true
    }
})
