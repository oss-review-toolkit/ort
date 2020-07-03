/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.reporters.SpdxDocumentReporter.FileFormat
import org.ossreviewtoolkit.spdx.SpdxLicense
import org.ossreviewtoolkit.spdx.SpdxModelSerializer.fromJson
import org.ossreviewtoolkit.spdx.SpdxModelSerializer.fromYaml
import org.ossreviewtoolkit.spdx.model.SpdxDocument
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.normalizeLineBreaks
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class SpdxDocumentReporterTest : WordSpec({
    "SpdxDocumentReporter" should {
        "create the expected JSON SPDX document" {
            val ortResult = createOrtResult()

            val jsonSpdxDocument = generateReport(ortResult, FileFormat.JSON)

            jsonSpdxDocument shouldBe patchExpectedResult(
                "src/funTest/assets/spdx-document-reporter-expected-output.spdx.json",
                fromJson(jsonSpdxDocument, SpdxDocument::class.java)
            )
        }

        "create the expected YAML SPDX document" {
            val ortResult = createOrtResult()

            val yamlSpdxDocument = generateReport(ortResult, FileFormat.YAML)

            yamlSpdxDocument shouldBe patchExpectedResult(
                "src/funTest/assets/spdx-document-reporter-expected-output.spdx.yml",
                fromYaml(yamlSpdxDocument, SpdxDocument::class.java)
            )
        }
    }
})

private fun generateReport(ortResult: OrtResult, format: FileFormat): String {
    val input = ReporterInput(ortResult)

    val outputDir = createTempDir(ORT_NAME, SpdxDocumentReporterTest::class.simpleName).apply { deleteOnExit() }

    val reportOptions = mapOf(
        SpdxDocumentReporter.CREATION_INFO_COMMENT to "some creation info comment",
        SpdxDocumentReporter.DOCUMENT_COMMENT to "some document comment",
        SpdxDocumentReporter.DOCUMENT_NAME to "some document name",
        SpdxDocumentReporter.OUTPUT_FILE_FORMATS to format.toString()
    )

    return SpdxDocumentReporter().generateReport(input, outputDir, reportOptions).single().readText()
        .normalizeLineBreaks()
}

private fun patchExpectedResult(expectedResultFile: String, actualSpdxDocument: SpdxDocument): String =
    patchExpectedResult(
        File(expectedResultFile),
        mapOf(
            "<REPLACE_LICENSE_LIST_VERSION>" to SpdxLicense.LICENSE_LIST_VERSION.substringBefore("-"),
            "<REPLACE_ORT_VERSION>" to Environment().ortVersion,
            "<REPLACE_CREATION_DATE_AND_TIME>" to actualSpdxDocument.creationInfo.created.toString(),
            "<REPLACE_DOCUMENT_NAMESPACE>" to actualSpdxDocument.documentNamespace
        )
    )

private fun createOrtResult(): OrtResult = OrtResult.EMPTY
