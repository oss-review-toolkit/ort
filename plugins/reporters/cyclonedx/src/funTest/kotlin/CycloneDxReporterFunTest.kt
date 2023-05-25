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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.file.aFile
import io.kotest.matchers.file.emptyFile
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.cyclonedx.parsers.JsonParser
import org.cyclonedx.parsers.XmlParser

import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.test.getAssetAsString

class CycloneDxReporterFunTest : WordSpec({
    val defaultSchemaVersion = CycloneDxReporter.DEFAULT_SCHEMA_VERSION.versionString
    val outputDir = tempdir()

    "BOM generation with single option" should {
        val optionSingle = mapOf("single.bom" to "true")

        "create just one file" {
            val jsonOptions = optionSingle + mapOf("output.file.formats" to "json")

            val bomFiles = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, jsonOptions)

            bomFiles shouldHaveSize 1
        }

        "be valid XML according to schema version $defaultSchemaVersion" {
            val xmlOptions = optionSingle + mapOf("output.file.formats" to "xml")

            val bomFile = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, xmlOptions).single()

            bomFile shouldBe aFile()
            bomFile shouldNotBe emptyFile()
            XmlParser().validate(bomFile, CycloneDxReporter.DEFAULT_SCHEMA_VERSION) should beEmpty()
        }

        "create the expected XML file" {
            val expectedBom = getAssetAsString("cyclonedx-reporter-expected-result.xml")
            val xmlOptions = optionSingle + mapOf("output.file.formats" to "xml")

            val bomFile = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, xmlOptions).single()
            val actualBom = bomFile.readText().patchCycloneDxResult().normalizeLineBreaks()

            actualBom shouldBe expectedBom
        }

        "be valid JSON according to schema version $defaultSchemaVersion" {
            val jsonOptions = optionSingle + mapOf("output.file.formats" to "json")

            val bomFile = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, jsonOptions).single()

            bomFile shouldBe aFile()
            bomFile shouldNotBe emptyFile()
            JsonParser().validate(bomFile, CycloneDxReporter.DEFAULT_SCHEMA_VERSION) should beEmpty()
        }

        "create the expected JSON file" {
            val expectedBom = getAssetAsString("cyclonedx-reporter-expected-result.json")
            val jsonOptions = optionSingle + mapOf("output.file.formats" to "json")

            val bomFile = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, jsonOptions).single()
            val actualBom = bomFile.readText().patchCycloneDxResult()

            actualBom shouldEqualJson expectedBom
        }
    }

    "BOM generation with multi option" should {
        val optionMulti = mapOf("single.bom" to "false")

        "create one file per project" {
            val jsonOptions = optionMulti + mapOf("output.file.formats" to "json")

            val bomFiles = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, jsonOptions)

            bomFiles shouldHaveSize 2
        }

        "generate valid XML files according to schema version $defaultSchemaVersion" {
            val xmlOptions = optionMulti + mapOf("output.file.formats" to "xml")

            val (bomFileProjectWithFindings, bomFileProjectWithoutFindings) = CycloneDxReporter()
                .generateReport(ReporterInput(ORT_RESULT), outputDir, xmlOptions).also {
                    it shouldHaveSize 2
                }

            bomFileProjectWithFindings shouldBe aFile()
            bomFileProjectWithFindings shouldNotBe emptyFile()
            XmlParser().validate(
                bomFileProjectWithFindings,
                CycloneDxReporter.DEFAULT_SCHEMA_VERSION
            ) should beEmpty()

            bomFileProjectWithoutFindings shouldBe aFile()
            bomFileProjectWithoutFindings shouldNotBe emptyFile()
            XmlParser().validate(
                bomFileProjectWithoutFindings,
                CycloneDxReporter.DEFAULT_SCHEMA_VERSION
            ) should beEmpty()
        }

        "generate valid JSON files according to schema version $defaultSchemaVersion" {
            val jsonOptions = optionMulti + mapOf("output.file.formats" to "json")

            val (bomFileProjectWithFindings, bomFileProjectWithoutFindings) = CycloneDxReporter()
                .generateReport(ReporterInput(ORT_RESULT), outputDir, jsonOptions).also {
                    it shouldHaveSize 2
                }

            bomFileProjectWithFindings shouldBe aFile()
            bomFileProjectWithFindings shouldNotBe emptyFile()
            JsonParser().validate(bomFileProjectWithFindings, CycloneDxReporter.DEFAULT_SCHEMA_VERSION) should beEmpty()

            bomFileProjectWithoutFindings shouldBe aFile()
            bomFileProjectWithoutFindings shouldNotBe emptyFile()
            JsonParser().validate(
                bomFileProjectWithoutFindings,
                CycloneDxReporter.DEFAULT_SCHEMA_VERSION
            ) should beEmpty()
        }

        "generate expected JSON files" {
            val expectedBomWithFindings = getAssetAsString("cyclonedx-reporter-expected-result-with-findings.json")
            val expectedBomWithoutFindings = getAssetAsString(
                "cyclonedx-reporter-expected-result-without-findings.json"
            )
            val jsonOptions = optionMulti + mapOf("output.file.formats" to "json")

            val (bomProjectWithFindings, bomProjectWithoutFindings) = CycloneDxReporter()
                .generateReport(ReporterInput(ORT_RESULT), outputDir, jsonOptions).also {
                    it shouldHaveSize 2
                }
            val actualBomWithFindings = bomProjectWithFindings.readText().patchCycloneDxResult()
            val actualBomWithoutFindings = bomProjectWithoutFindings.readText().patchCycloneDxResult()

            actualBomWithFindings shouldEqualJson expectedBomWithFindings
            actualBomWithoutFindings shouldEqualJson expectedBomWithoutFindings
        }
    }
})

private fun String.patchCycloneDxResult() =
    replace(
        """urn:uuid:[a-f0-9]{8}(?:-[a-f0-9]{4}){4}[a-f0-9]{8}""".toRegex(),
        "urn:uuid:01234567-0123-0123-0123-01234567"
    )
