/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.aosd

import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.test.getAssetFile

class Aosd21ReporterFunTest : WordSpec({
    "The example JSON report" should {
        "be valid according to the schema" {
            val schemaFile = getAssetFile("aosd21/AOSD2.1_Importscheme_V2.1.0.json")
            val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schemaFile.toURI())

            val exampleFile = getAssetFile("aosd21/AOSD2.1_Example_Json_Import_File_V2.1.0.json")
            val errors = schema.validate(exampleFile.readText(), InputFormat.JSON)

            errors should beEmpty()
        }

        "deserialize correctly" {
            val aosdFile = getAssetFile("aosd21/AOSD2.1_Example_Json_Import_File_V2.1.0.json")
            val aosd = aosdFile.readAosd21Report()

            with(aosd) {
                schemaVersion shouldBe "2.1.0"
                externalId shouldBe "myownId"
                scanned shouldBe true
                directDependencies shouldHaveSize 10
                components shouldHaveSize 13
            }
        }
    }

    "The generated report" should {
        val outputDir = tempdir()
        val reportFiles = Aosd21Reporter().generateReport(ReporterInput(ORT_RESULT), outputDir)

        "be valid according to the schema" {
            val schemaFile = getAssetFile("aosd21/AOSD2.1_Importscheme_V2.1.0.json")
            val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schemaFile.toURI())

            reportFiles.forAll {
                it shouldBeSuccess { reportFile ->
                    val errors = schema.validate(reportFile.readText(), InputFormat.JSON)
                    errors should beEmpty()
                }
            }
        }

        "match the expected result" {
            reportFiles shouldHaveSize 2

            assertSoftly {
                with(reportFiles[0]) {
                    this shouldBeSuccess { actualFile ->
                        val expectedFile = getAssetFile("aosd21/aosd.NPM-%40ort-project-with-findings-1.0.json")
                        actualFile.readText() shouldEqualJson expectedFile.readText()
                    }
                }

                with(reportFiles[1]) {
                    this shouldBeSuccess { actualFile ->
                        val expectedFile = getAssetFile("aosd21/aosd.NPM-%40ort-project-without-findings-1.0.json")
                        actualFile.readText() shouldEqualJson expectedFile.readText()
                    }
                }
            }
        }
    }
})
