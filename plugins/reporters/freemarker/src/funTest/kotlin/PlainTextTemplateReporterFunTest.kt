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

package org.ossreviewtoolkit.plugins.reporters.freemarker

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration
import org.ossreviewtoolkit.model.config.FileStorageConfiguration
import org.ossreviewtoolkit.model.config.LocalFileStorageConfiguration
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.licenses.LicenseCategorization
import org.ossreviewtoolkit.model.licenses.LicenseCategory
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.plugins.licensefactproviders.spdx.SpdxLicenseFactProviderFactory
import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.extractResource
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.test.readResource

class PlainTextTemplateReporterFunTest : WordSpec({
    "The notice default template" should {
        "generate the correct license notes" {
            val expectedText = readResource("/plain-text-template-reporter-expected-results")

            val report = generateReport(ORT_RESULT)

            report shouldBe expectedText
        }

        "generate the correct license notes with archived license files" {
            val expectedText = readResource("/plain-text-template-reporter-expected-results-with-license-files")
            val archiveDir = tempdir() / "archive"
            extractResource("/archive2.zip", archiveDir / "195e41f4a5cea6fb5ff309e452d03c11b4ea6511" / "archive.zip")
            extractResource("/archive2.zip", archiveDir / "32b1dee054b06e5037b30ca94e2ff06a6d12f6b9" / "archive.zip")
            val config = OrtConfiguration(
                scanner = ScannerConfiguration(
                    archive = FileArchiverConfiguration(
                        fileStorage = FileStorageConfiguration(
                            localFileStorage = LocalFileStorageConfiguration(
                                directory = archiveDir,
                                compression = false
                            )
                        )
                    )
                )
            )

            val report = generateReport(ORT_RESULT, config)

            report shouldBe expectedText
        }
    }

    "The notice summary template" should {
        "generate the correct license notes" {
            val expectedText = readResource("/plain-text-template-reporter-expected-results-summary")

            val report = generateReport(
                ORT_RESULT,
                config = PlainTextTemplateReporterConfig(templateIds = listOf("NOTICE_SUMMARY"), templatePaths = null)
            )

            report shouldBe expectedText
        }
    }
})

private fun TestConfiguration.generateReport(
    ortResult: OrtResult,
    ortConfig: OrtConfiguration = OrtConfiguration(),
    config: PlainTextTemplateReporterConfig = PlainTextTemplateReporterConfig(templateIds = null, templatePaths = null)
): String {
    val input = ReporterInput(
        ortResult,
        ortConfig,
        licenseClassifications = createLicenseClassifications(),
        licenseFactProvider = SpdxLicenseFactProviderFactory.create()
    )

    val outputDir = tempdir()

    return PlainTextTemplateReporter(config = config).generateReport(input, outputDir).single().getOrThrow().readText()
}

private fun createLicenseClassifications(): LicenseClassifications {
    val includeNoticeCategory = LicenseCategory("include-in-notice-file")
    val includeSourceCategory = LicenseCategory("include-source-code-offer-in-notice-file")
    val mitLicense = LicenseCategorization(
        SpdxSingleLicenseExpression.parse("MIT"), setOf(includeNoticeCategory.name)
    )
    val bsdLicense = LicenseCategorization(
        SpdxSingleLicenseExpression.parse("BSD-3-Clause"), setOf(includeSourceCategory.name)
    )
    return LicenseClassifications(
        categories = listOf(includeNoticeCategory, includeSourceCategory),
        categorizations = listOf(mitLicense, bsdLicense)
    )
}
