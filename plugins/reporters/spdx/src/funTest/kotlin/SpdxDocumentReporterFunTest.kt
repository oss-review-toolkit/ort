/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.spdx

import io.kotest.assertions.json.schema.parseSchema
import io.kotest.assertions.json.schema.shouldMatchSchema
import io.kotest.common.ExperimentalKotest
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.ort.ORT_VERSION
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdxdocument.SpdxModelMapper.FileFormat
import org.ossreviewtoolkit.utils.spdxdocument.SpdxModelMapper.fromJson
import org.ossreviewtoolkit.utils.spdxdocument.SpdxModelMapper.fromYaml
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxDocument
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.readOrtResult
import org.ossreviewtoolkit.utils.test.readResource

class SpdxDocumentReporterFunTest : WordSpec({
    "Reporting to JSON" should {
        @OptIn(ExperimentalKotest::class)
        "create a valid document" {
            val schema = parseSchema(readResource("/spdx-schema.json"))

            val jsonSpdxDocument = generateReport(ORT_RESULT, FileFormat.JSON)

            jsonSpdxDocument shouldMatchSchema schema
        }

        "create the expected document for a synthetic ORT result" {
            val expectedResult = readResource("/spdx-document-reporter-expected-output.spdx.json")

            val jsonSpdxDocument = generateReport(ORT_RESULT, FileFormat.JSON)

            jsonSpdxDocument shouldBe patchExpectedResult(
                expectedResult,
                custom = fromJson<SpdxDocument>(jsonSpdxDocument).getCustomReplacements()
            )
        }

        "omit file information if the corresponding option is disabled" {
            val jsonSpdxDocument = generateReport(
                ORT_RESULT,
                FileFormat.JSON,
                defaultConfig.copy(fileInformationEnabled = false)
            )

            val document = fromJson<SpdxDocument>(jsonSpdxDocument)

            document.files should beEmpty()
        }
    }

    "Reporting to YAML" should {
        "create the expected document for a synthetic ORT result" {
            val expectedResult = readResource("/spdx-document-reporter-expected-output.spdx.yml")

            val yamlSpdxDocument = generateReport(ORT_RESULT, FileFormat.YAML)

            yamlSpdxDocument shouldBe patchExpectedResult(
                expectedResult,
                custom = fromYaml<SpdxDocument>(yamlSpdxDocument).getCustomReplacements()
            )
        }

        "create the expected document for the ORT result of a Go project" {
            val ortResultForGoProject = readOrtResult("/disclosure-cli-scan-result.yml")
            val expectedResult = readResource("/disclosure-cli-expected-output.spdx.yml")

            val yamlSpdxDocument = generateReport(ortResultForGoProject, FileFormat.YAML)

            yamlSpdxDocument shouldBe patchExpectedResult(
                expectedResult,
                custom = fromYaml<SpdxDocument>(yamlSpdxDocument).getCustomReplacements()
            )
        }
    }
})

private val defaultConfig = SpdxDocumentReporterConfig(
    creationInfoComment = "some creation info comment",
    creationInfoPerson = "some creation info person",
    creationInfoOrganization = "some creation info organization",
    documentComment = "some document comment",
    documentName = "some document name",
    fileInformationEnabled = true,
    outputFileFormats = emptyList()
)

private fun TestConfiguration.generateReport(
    ortResult: OrtResult,
    format: FileFormat,
    config: SpdxDocumentReporterConfig = defaultConfig
): String {
    val input = ReporterInput(ortResult)

    val outputDir = tempdir()

    return SpdxDocumentReporter(config = config.copy(outputFileFormats = listOf(format.name)))
        .generateReport(input, outputDir)
        .single()
        .getOrThrow()
        .readText()
        .normalizeLineBreaks()
}

private fun SpdxDocument.getCustomReplacements() =
    mapOf(
        "<REPLACE_LICENSE_LIST_VERSION>" to SpdxLicense.LICENSE_LIST_VERSION.split('.').take(2).joinToString("."),
        "<REPLACE_ORT_VERSION>" to ORT_VERSION,
        "<REPLACE_CREATION_DATE_AND_TIME>" to creationInfo.created.toString(),
        "<REPLACE_DOCUMENT_NAMESPACE>" to documentNamespace
    )
