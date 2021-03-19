/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.scanner.scanners

import io.kotest.core.Tag
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forAll
import io.kotest.matchers.file.shouldNotStartWithPath
import io.kotest.matchers.shouldBe

import java.io.File

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.LocalScanner
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

abstract class AbstractScannerFunTest(testTags: Set<Tag> = emptySet()) : StringSpec() {
    protected val downloaderConfig = DownloaderConfiguration()
    protected val scannerConfig = ScannerConfiguration()

    // This is loosely based on the patterns from
    // https://github.com/licensee/licensee/blob/6c0f803/lib/licensee/project_files/license_file.rb#L6-L43.
    private val commonlyDetectedFiles = listOf("LICENSE", "LICENCE", "COPYING")

    private lateinit var inputDir: File
    private lateinit var outputDir: File

    abstract val scanner: LocalScanner
    abstract val expectedFileLicenses: Set<SpdxExpression>
    abstract val expectedDirectoryLicenses: Set<SpdxExpression>

    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        inputDir = createTempDirectory("$ORT_NAME-${javaClass.simpleName}").toFile()

        // Copy our own root license under different names to a temporary directory so we have something to operate on.
        val ortLicense = File("../LICENSE")
        commonlyDetectedFiles.forEach { ortLicense.copyTo(inputDir.resolve(it), overwrite = true) }
    }

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        outputDir = createTempDirectory("$ORT_NAME-${javaClass.simpleName}").toFile()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        outputDir.safeDeleteRecursively()
        super.afterTest(testCase, result)
    }

    override fun afterSpec(spec: Spec) {
        inputDir.safeDeleteRecursively()
        super.afterSpec(spec)
    }

    init {
        "Scanning a single file succeeds".config(tags = testTags) {
            val result = scanner.scanPath(inputDir.resolve("LICENSE"), outputDir)
            val summary = result.scanner?.results?.scanResults?.singleOrNull()?.singleOrNull()?.summary

            summary shouldNotBeNull {
                fileCount shouldBe 1
                licenses shouldBe expectedFileLicenses
                licenseFindings.forAll {
                    File(it.location.path) shouldNotStartWithPath inputDir
                }
            }
        }

        "Scanning a directory succeeds".config(tags = testTags) {
            val result = scanner.scanPath(inputDir, outputDir)
            val summary = result.scanner?.results?.scanResults?.singleOrNull()?.singleOrNull()?.summary

            summary shouldNotBeNull {
                fileCount shouldBe commonlyDetectedFiles.size
                licenses shouldBe expectedDirectoryLicenses
                licenseFindings.forAll {
                    File(it.location.path) shouldNotStartWithPath inputDir
                }
            }
        }
    }
}

private fun <K, V> Map<K, V>.singleOrNull() = entries.singleOrNull()?.value
