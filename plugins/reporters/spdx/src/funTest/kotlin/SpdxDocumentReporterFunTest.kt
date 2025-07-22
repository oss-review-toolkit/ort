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

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.plugins.licensefactproviders.scancode.ScanCodeLicenseFactProviderFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.ort.ORT_VERSION
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdxdocument.SpdxModelMapper.FileFormat
import org.ossreviewtoolkit.utils.spdxdocument.SpdxModelMapper.fromJson
import org.ossreviewtoolkit.utils.spdxdocument.SpdxModelMapper.fromYaml
import org.ossreviewtoolkit.utils.spdxdocument.model.SPDX_VERSION_2_2
import org.ossreviewtoolkit.utils.spdxdocument.model.SPDX_VERSION_2_3
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxDocument
import org.ossreviewtoolkit.utils.test.InputFormat
import org.ossreviewtoolkit.utils.test.matchJsonSchema
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.readResource

class SpdxDocumentReporterFunTest : WordSpec({
    "Reporting to SPDX-2.2" should {
        val schemaJson = readResource("/v2.2.2/spdx-schema.json")

        "create the expected JSON document for a synthetic scan result" {
            val expectedResult = readResource("/v2.2.2/synthetic-scan-result-expected-output.spdx.json")

            val jsonSpdxDocument = generateReport(ORT_RESULT, FileFormat.JSON, SPDX_VERSION_2_2)

            jsonSpdxDocument should matchJsonSchema(schemaJson)
            jsonSpdxDocument shouldBe patchExpectedResult(
                expectedResult,
                custom = fromJson<SpdxDocument>(jsonSpdxDocument).getCustomReplacements()
            )
        }

        "create the expected YAML document for a synthetic scan result" {
            val expectedResult = readResource("/v2.2.2/synthetic-scan-result-expected-output.spdx.yml")

            val yamlSpdxDocument = generateReport(ORT_RESULT, FileFormat.YAML, SPDX_VERSION_2_2)

            yamlSpdxDocument should matchJsonSchema(schemaJson, InputFormat.YAML)
            yamlSpdxDocument shouldBe patchExpectedResult(
                expectedResult,
                custom = fromYaml<SpdxDocument>(yamlSpdxDocument).getCustomReplacements()
            )
        }

        "omit file information if the corresponding option is disabled" {
            val jsonSpdxDocument = generateReport(
                ORT_RESULT,
                FileFormat.JSON,
                SPDX_VERSION_2_2,
                fileInformationEnabled = false
            )
            val document = fromJson<SpdxDocument>(jsonSpdxDocument)

            jsonSpdxDocument should matchJsonSchema(schemaJson)
            document.files should beEmpty()
        }
    }

    "Reporting to SPDX-2.3" should {
        val schemaJson = readResource("/v2.3/spdx-schema.json")

        "create the expected JSON document for a synthetic scan result" {
            val expectedResult = readResource("/v2.3/synthetic-scan-result-expected-output.spdx.json")

            val jsonSpdxDocument = generateReport(ORT_RESULT, FileFormat.JSON, SPDX_VERSION_2_3)

            jsonSpdxDocument should matchJsonSchema(schemaJson)
            jsonSpdxDocument shouldBe patchExpectedResult(
                expectedResult,
                custom = fromJson<SpdxDocument>(jsonSpdxDocument).getCustomReplacements()
            )
        }

        "create the expected YAML document for a synthetic scan result" {
            val expectedResult = readResource("/v2.3/synthetic-scan-result-expected-output.spdx.yml")

            val yamlSpdxDocument = generateReport(ORT_RESULT, FileFormat.YAML, SPDX_VERSION_2_3)

            yamlSpdxDocument should matchJsonSchema(schemaJson, InputFormat.YAML)
            yamlSpdxDocument shouldBe patchExpectedResult(
                expectedResult,
                custom = fromYaml<SpdxDocument>(yamlSpdxDocument).getCustomReplacements()
            )
        }

        "omit file information if the corresponding option is disabled" {
            val jsonSpdxDocument = generateReport(
                ORT_RESULT,
                FileFormat.JSON,
                SPDX_VERSION_2_3,
                fileInformationEnabled = false
            )
            val document = fromJson<SpdxDocument>(jsonSpdxDocument)

            jsonSpdxDocument should matchJsonSchema(schemaJson)
            document.files should beEmpty()
        }
    }
})

private fun TestConfiguration.generateReport(
    ortResult: OrtResult,
    format: FileFormat,
    spdxVersion: String,
    fileInformationEnabled: Boolean = true
): String {
    val config = SpdxDocumentReporterConfig(
        spdxVersion = spdxVersion,
        creationInfoComment = "some creation info comment",
        creationInfoPerson = "some creation info person",
        creationInfoOrganization = "some creation info organization",
        documentComment = "some document comment",
        documentName = "some document name",
        fileInformationEnabled = fileInformationEnabled,
        outputFileFormats = listOf(format.name)
    )

    return SpdxDocumentReporter(config = config)
        .generateReport(
            input = ReporterInput(ortResult, licenseFactProvider = ScanCodeLicenseFactProviderFactory.create()),
            outputDir = tempdir()
        )
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
