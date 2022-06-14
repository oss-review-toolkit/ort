/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 * Copyright (C) 2020 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.reporter.reporters

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.file.aFile
import io.kotest.matchers.file.emptyFile
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import java.io.File

import org.cyclonedx.parsers.JsonParser
import org.cyclonedx.parsers.XmlParser

import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.patchCycloneDxResult
import org.ossreviewtoolkit.utils.test.createSpecTempDir

class CycloneDxReporterFunTest : WordSpec({
    val defaultSchemaVersion = CycloneDxReporter.DEFAULT_SCHEMA_VERSION.versionString
    val optionSingle = mapOf("single.bom" to "true")
    val optionMulti = mapOf("single.bom" to "false")
    val outputDir = createSpecTempDir()

    "BOM generation with single option" should {
        "be composed with just one file" {
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
            val expectedBom = File("src/funTest/assets/bom.cyclonedx.xml").readText()

            val xmlOptions = optionSingle + mapOf("output.file.formats" to "xml")
            val bomFile = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, xmlOptions).single()

            bomFile.readText().patchCycloneDxResult() shouldBe expectedBom
        }

        "be valid JSON according to schema version $defaultSchemaVersion" {
            val jsonOptions = optionSingle + mapOf("output.file.formats" to "json")
            val bomFile = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, jsonOptions).single()

            bomFile shouldBe aFile()
            bomFile shouldNotBe emptyFile()
            JsonParser().validate(bomFile, CycloneDxReporter.DEFAULT_SCHEMA_VERSION) should beEmpty()
        }

        "create the expected JSON file" {
            val expectedBom = File("src/funTest/assets/bom.cyclonedx.json").readText()

            val jsonOptions = optionSingle + mapOf("output.file.formats" to "json")
            val bomFile = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, jsonOptions).single()

            bomFile.readText().patchCycloneDxResult() shouldEqualJson expectedBom
        }
    }

    "BOM generation with multi option" should {
        "be composed with one file by project not excluded" {
            val jsonOptions = optionMulti + mapOf("output.file.formats" to "json")
            val bomFiles = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, jsonOptions)

            bomFiles shouldHaveSize 2
        }

        "generate valid XML files according to schema version $defaultSchemaVersion" {
            val xmlOptions = optionMulti + mapOf("output.file.formats" to "xml")
            val bomFile = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, xmlOptions)

            val bomFileProjectWithFindings = bomFile[0]
            val bomFileProjectWithoutFindings = bomFile[1]

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

        "generate expected XML files" {
            val expectedBomWithFindings =
                File("src/funTest/assets/bom.cyclonedx-NPM-%40ort-project-with-findings-1.0.xml").readText()
            val expectedBomWithoutFindings =
                File("src/funTest/assets/bom.cyclonedx-NPM-%40ort-project-without-findings-1.0.xml").readText()
            val xmlOptions = optionMulti + mapOf("output.file.formats" to "xml")
            val bomFile = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, xmlOptions)

            val bomProjectWithFindings = bomFile[0].readText()
            val bomProjectWithoutFindings = bomFile[1].readText()

            bomProjectWithFindings.patchCycloneDxResult() shouldBe expectedBomWithFindings
            bomProjectWithoutFindings.patchCycloneDxResult() shouldBe expectedBomWithoutFindings
        }

        "generate valid JSON files according to schema version $defaultSchemaVersion" {
            val jsonOptions = optionMulti + mapOf("output.file.formats" to "json")
            val bomFile = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, jsonOptions)

            val bomFileProjectWithFindings = bomFile[0]
            val bomFileProjectWithoutFindings = bomFile[1]

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
            val expectedBomWithFindings =
                File("src/funTest/assets/bom.cyclonedx-NPM-%40ort-project-with-findings-1.0.json").readText()
            val expectedBomWithoutFindings =
                File("src/funTest/assets/bom.cyclonedx-NPM-%40ort-project-without-findings-1.0.json")
                    .readText()
            val jsonOptions = optionMulti + mapOf("output.file.formats" to "json")
            val bomFile = CycloneDxReporter().generateReport(ReporterInput(ORT_RESULT), outputDir, jsonOptions)

            val bomProjectWithFindings = bomFile[0].readText()
            val bomProjectWithoutFindings = bomFile[1].readText()

            bomProjectWithFindings.patchCycloneDxResult() shouldEqualJson expectedBomWithFindings
            bomProjectWithoutFindings.patchCycloneDxResult() shouldEqualJson expectedBomWithoutFindings
        }
    }
})
