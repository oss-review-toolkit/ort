/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.cyclonedx

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.file.aFile
import io.kotest.matchers.file.emptyFile
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.cyclonedx.parsers.JsonParser
import org.cyclonedx.parsers.XmlParser

import org.ossreviewtoolkit.plugins.licensefactproviders.spdx.SpdxLicenseFactProviderFactory
import org.ossreviewtoolkit.plugins.reporters.cyclonedx.CycloneDxReporter.Companion.REPORT_BASE_FILENAME
import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ORT_RESULT_WITH_ILLEGAL_COPYRIGHTS
import org.ossreviewtoolkit.reporter.ORT_RESULT_WITH_VULNERABILITIES
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.test.readResource

class CycloneDxReporterFunTest : WordSpec({
    val defaultSchemaVersion = DEFAULT_SCHEMA_VERSION.versionString
    val outputDir = tempdir()

    "BOM generation with single option" should {
        "create just one file" {
            val bomFileResults = CycloneDxReporterFactory.create(
                singleBom = true,
                outputFileFormats = listOf("json")
            ).generateReport(ReporterInput(ORT_RESULT), outputDir)

            bomFileResults.shouldBeSingleton {
                it shouldBeSuccess outputDir / "$REPORT_BASE_FILENAME.json"
            }
        }

        "be valid XML according to schema version $defaultSchemaVersion" {
            val bomFileResults = CycloneDxReporterFactory.create(
                singleBom = true,
                outputFileFormats = listOf("xml")
            ).generateReport(ReporterInput(ORT_RESULT), outputDir)

            bomFileResults.shouldBeSingleton {
                it shouldBeSuccess { bomFile ->
                    bomFile shouldBe aFile()
                    bomFile shouldNotBe emptyFile()
                    XmlParser().validate(bomFile, DEFAULT_SCHEMA_VERSION) should beEmpty()
                }
            }
        }

        "create the expected XML file" {
            val expectedBom = readResource("/cyclonedx-reporter-expected-result.xml")

            val bomFileResults = CycloneDxReporterFactory.create(
                singleBom = true,
                outputFileFormats = listOf("xml")
            ).generateReport(
                ReporterInput(
                    ORT_RESULT_WITH_VULNERABILITIES,
                    licenseFactProvider = SpdxLicenseFactProviderFactory.create()
                ),
                outputDir
            )

            bomFileResults.shouldBeSingleton {
                it shouldBeSuccess { bomFile ->
                    bomFile shouldBe aFile()
                    bomFile shouldNotBe emptyFile()

                    val actualBom = bomFile.readText().patchCycloneDxResult().normalizeLineBreaks()
                    actualBom shouldBe expectedBom
                }
            }
        }

        "the expected XML file even if some copyrights contain non printable characters" {
            val bomFileResults = CycloneDxReporterFactory.create(
                singleBom = true,
                outputFileFormats = listOf("xml")
            ).generateReport(ReporterInput(ORT_RESULT_WITH_ILLEGAL_COPYRIGHTS), outputDir)

            bomFileResults.shouldBeSingleton {
                it shouldBeSuccess { bomFile ->
                    bomFile shouldBe aFile()
                    bomFile shouldNotBe emptyFile()
                }
            }
        }

        "be valid JSON according to schema version $defaultSchemaVersion" {
            val bomFileResults = CycloneDxReporterFactory.create(
                singleBom = true,
                outputFileFormats = listOf("json")
            ).generateReport(ReporterInput(ORT_RESULT_WITH_VULNERABILITIES), outputDir)

            bomFileResults.shouldBeSingleton {
                it shouldBeSuccess { bomFile ->
                    bomFile shouldBe aFile()
                    bomFile shouldNotBe emptyFile()
                    JsonParser().validate(bomFile, DEFAULT_SCHEMA_VERSION) should beEmpty()
                }
            }
        }

        "create the expected JSON file" {
            val expectedBom = readResource("/cyclonedx-reporter-expected-result.json")

            val bomFileResults = CycloneDxReporterFactory.create(
                singleBom = true,
                outputFileFormats = listOf("json")
            ).generateReport(
                ReporterInput(
                    ORT_RESULT_WITH_VULNERABILITIES,
                    licenseFactProvider = SpdxLicenseFactProviderFactory.create()
                ),
                outputDir
            )

            bomFileResults.shouldBeSingleton {
                it shouldBeSuccess { bomFile ->
                    bomFile shouldBe aFile()
                    bomFile shouldNotBe emptyFile()

                    val actualBom = bomFile.readText().patchCycloneDxResult()
                    actualBom shouldEqualJson expectedBom
                }
            }
        }
    }

    "BOM generation with multi option" should {
        "create one file per project" {
            val bomFileResults = CycloneDxReporterFactory.create(
                singleBom = false,
                outputFileFormats = listOf("json")
            ).generateReport(ReporterInput(ORT_RESULT_WITH_VULNERABILITIES), outputDir)

            bomFileResults shouldHaveSize 2
            bomFileResults.forAll { it.shouldBeSuccess() }
        }

        "generate valid XML files according to schema version $defaultSchemaVersion" {
            val (bomFileResultWithFindings, bomFileResultWithoutFindings) =
                CycloneDxReporterFactory.create(
                    singleBom = false,
                    outputFileFormats = listOf("xml")
                ).generateReport(ReporterInput(ORT_RESULT_WITH_VULNERABILITIES), outputDir).also {
                    it shouldHaveSize 2
                }

            bomFileResultWithFindings shouldBeSuccess { bomFile ->
                bomFile shouldBe aFile()
                bomFile shouldNotBe emptyFile()
                XmlParser().validate(bomFile, DEFAULT_SCHEMA_VERSION) should beEmpty()
            }

            bomFileResultWithoutFindings shouldBeSuccess { bomFile ->
                bomFile shouldBe aFile()
                bomFile shouldNotBe emptyFile()
                XmlParser().validate(bomFile, DEFAULT_SCHEMA_VERSION) should beEmpty()
            }
        }

        "generate valid JSON files according to schema version $defaultSchemaVersion" {
            val (bomFileResultWithFindings, bomFileResultWithoutFindings) =
                CycloneDxReporterFactory.create(
                    singleBom = false,
                    outputFileFormats = listOf("json")
                ).generateReport(ReporterInput(ORT_RESULT_WITH_VULNERABILITIES), outputDir).also {
                    it shouldHaveSize 2
                }

            bomFileResultWithFindings shouldBeSuccess { bomFile ->
                bomFile shouldBe aFile()
                bomFile shouldNotBe emptyFile()
                JsonParser().validate(bomFile, DEFAULT_SCHEMA_VERSION) should beEmpty()
            }

            bomFileResultWithoutFindings shouldBeSuccess { bomFile ->
                bomFile shouldBe aFile()
                bomFile shouldNotBe emptyFile()
                JsonParser().validate(bomFile, DEFAULT_SCHEMA_VERSION) should beEmpty()
            }
        }

        "generate expected JSON files" {
            val (bomFileResultWithFindings, bomFileResultWithoutFindings) =
                CycloneDxReporterFactory.create(
                    singleBom = false,
                    outputFileFormats = listOf("json")
                ).generateReport(
                    ReporterInput(
                        ORT_RESULT,
                        licenseFactProvider = SpdxLicenseFactProviderFactory.create()
                    ),
                    outputDir
                ).also { it shouldHaveSize 2 }

            bomFileResultWithFindings shouldBeSuccess { bomFile ->
                val expectedBom = readResource("/cyclonedx-reporter-expected-result-with-findings.json")
                val actualBom = bomFile.readText().patchCycloneDxResult()

                actualBom shouldEqualJson expectedBom
            }

            bomFileResultWithoutFindings shouldBeSuccess { bomFile ->
                val expectedBom = readResource("/cyclonedx-reporter-expected-result-without-findings.json")
                val actualBom = bomFile.readText().patchCycloneDxResult()

                actualBom shouldEqualJson expectedBom
            }
        }
    }
})

private fun String.patchCycloneDxResult(): String =
    replaceFirst(
        """urn:uuid:[a-f0-9]{8}(?:-[a-f0-9]{4}){4}[a-f0-9]{8}""".toRegex(),
        "urn:uuid:12345678-1234-1234-1234-123456789012"
    ).replaceFirst(
        """(timestamp[>"](\s*:\s*")?)\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""".toRegex(),
        "$11970-01-01T00:00:00Z"
    ).replaceFirst(
        """(version[>"](\s*:\s*")?)[\w.+-]+""".toRegex(),
        "$1deadbeef"
    )
