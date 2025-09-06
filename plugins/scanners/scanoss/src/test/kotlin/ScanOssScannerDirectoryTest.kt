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

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.mockk.spyk

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
import org.ossreviewtoolkit.utils.common.extractResource
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * A test for scanning a directory with the [ScanOss] scanner.
 */
class ScanOssScannerDirectoryTest : StringSpec({
    lateinit var scanner: ScanOss

    val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
            .usingFilesUnderClasspath("scanMulti")
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
        val pathToDir = tempdir().apply {
            extractResource("/filesToScan/random-data-05-06-11.kt", resolve("random-data-05-06-11.kt"))
            extractResource("/filesToScan/random-data-05-07-04.kt", resolve("random-data-05-07-04.kt"))
        }

        val summary = scanner.scanPath(
            pathToDir,
            ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE)
        )

        with(summary) {
            licenseFindings should containExactlyInAnyOrder(
                LicenseFinding(
                    license = "Apache-2.0",
                    location = TextLocation(
                        path = "scanner/src/main/kotlin/random-data-05-07-04.kt",
                        line = TextLocation.UNKNOWN_LINE
                    ),
                    score = 100.0f
                )
            )

            snippetFindings should containExactly(
                SnippetFinding(
                    TextLocation("utils/src/main/kotlin/random-data-05-06-11.kt", 1, 240),
                    setOf(
                        Snippet(
                            99.0f,
                            TextLocation(
                                "examples/example.rules.kts",
                                128,
                                367
                            ),
                            RepositoryProvenance(
                                VcsInfo(VcsType.GIT, "https://github.com/scanoss/ort.git", ""), "."
                            ),
                            "pkg:github/scanoss/ort",
                            SpdxExpression.parse("Apache-2.0"),
                            mapOf(
                                "file_hash" to "871fb0c5188c2f620d9b997e225b0095",
                                "file_url" to "https://osskb.org/api/file_contents/871fb0c5188c2f620d9b997e225b0095",
                                "source_hash" to "2e91edbe430c4eb195a977d326d6d6c0"
                            )
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

        val pathToDir = tempdir().apply {
            extractResource("/exclusionTest/random-data-05-04-43.kt", resolve("random-data-05-04-43.kt"))
            extractResource("/exclusionTest/random-data-05-05-29.kt", resolve("random-data-05-05-29.kt"))
            extractResource("/exclusionTest/random-data-10-41-29.go", resolve("random-data-10-41-29.go"))
        }

        // Run the scanner with our exclusion pattern. This will traverse the directory and should skip .kt files.
        scanner.scanPath(
            pathToDir,
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

        // Extract included filenames using a regex pattern from the SCANOSS HTTP POST.
        // The pattern matches lines starting with "file=" followed by hash and size, then captures the filename.
        val filenamePattern = "file=.*?,.*?,(.+)".toRegex(RegexOption.MULTILINE)
        val includedFiles = requestBodies.flatMap { body ->
            filenamePattern.findAll(body).map { it.groupValues[1] }.toList()
        }

        // Verify that .kt files were excluded from the scan.
        // These assertions check that Kotlin files are not present in the API requests.
        includedFiles.any { "random-data-05-04-43.kt" in it } shouldBe false
        includedFiles.any { "random-data-05-05-29.kt" in it } shouldBe false

        // Verify that non-.kt files were included in the scan.
        // This assertion checks that our Go file was sent to the API.
        includedFiles.any { "random-data-10-41-29.go" in it } shouldBe true
    }

    "Scanner should obfuscate file paths when path obfuscation is enabled" {
        val scannerWithObfuscation = spyk(
            ScanOssFactory.create(
                apiUrl = "http://localhost:${server.port()}",
                apiKey = Secret(""),
                enablePathObfuscation = true
            )
        )

        val pathToDir = tempdir().apply {
            extractResource("/filesToScan/random-data-05-06-11.kt", resolve("random-data-05-06-11.kt"))
            extractResource("/filesToScan/random-data-05-07-04.kt", resolve("random-data-05-07-04.kt"))
        }

        // Run the scanner with path obfuscation enabled.
        scannerWithObfuscation.scanPath(
            pathToDir,
            ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE)
        )

        // Retrieve all HTTP POST requests captured by WireMock during the scan.
        val requests = server.findAll(WireMock.postRequestedFor(WireMock.anyUrl()))
        val requestBodies = requests.map { it.bodyAsString }

        // Extract included filenames using a regex pattern from the SCANOSS HTTP POST.
        val filenamePattern = "^file=.*?,.*?,(.+)$".toRegex(RegexOption.MULTILINE)
        val includedFiles = requestBodies.flatMap { body ->
            filenamePattern.findAll(body).map { it.groupValues[1] }.toList()
        }

        // When path obfuscation is enabled, the filenames should be obfuscated (not contain original file names).
        includedFiles.any { "random-data-05-06-11.kt" in it } shouldBe false
        includedFiles.any { "random-data-05-07-04.kt" in it } shouldBe false

        // The requests should still contain some files (obfuscated names).
        includedFiles shouldNot beEmpty()
    }

    "Scanner should return original file paths in results when path obfuscation is enabled" {
        val scannerWithObfuscation = spyk(
            ScanOssFactory.create(
                apiUrl = "http://localhost:${server.port()}",
                apiKey = Secret(""),
                enablePathObfuscation = true
            )
        )

        val pathToDir = tempdir().apply {
            extractResource("/filesToScan/random-data-05-06-11.kt", resolve("random-data-05-06-11.kt"))
            extractResource("/filesToScan/random-data-05-07-04.kt", resolve("random-data-05-07-04.kt"))
        }

        val summary = scannerWithObfuscation.scanPath(
            pathToDir,
            ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE)
        )

        // Even with path obfuscation enabled for server requests, the results should contain original paths.
        with(summary) {
            // Verify that license findings contain the original file paths.
            licenseFindings.any { finding ->
                finding.location.path == "scanner/src/main/kotlin/random-data-05-07-04.kt"
            } shouldBe true

            // Verify that snippet findings contain the original file paths.
            snippetFindings.any { finding ->
                finding.sourceLocation.path == "utils/src/main/kotlin/random-data-05-06-11.kt"
            } shouldBe true
        }
    }
})
