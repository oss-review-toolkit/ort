/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import java.io.OutputStream

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFindingsMap
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.licenses.LicenseConfiguration
import org.ossreviewtoolkit.model.utils.PackageConfigurationProvider
import org.ossreviewtoolkit.model.utils.collectConcludedLicenses
import org.ossreviewtoolkit.model.utils.collectDeclaredLicenses
import org.ossreviewtoolkit.model.utils.collectLicenseFindings
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ScriptRunner

abstract class AbstractNoticeReporter : Reporter {
    companion object {
        const val DEFAULT_HEADER_WITH_LICENSES =
            "This project contains or depends on third-party software components pursuant to the following licenses:\n"
        const val DEFAULT_HEADER_WITHOUT_LICENSES =
            "This project neither contains or depends on any third-party software components.\n"
        const val NOTICE_SEPARATOR = "\n----\n\n"
    }

    data class NoticeReportModel(
        val headers: List<String>,
        val headerWithLicenses: String,
        val headerWithoutLicenses: String,
        val findings: Map<Identifier, LicenseFindingsMap>,
        val footers: List<String>
    )

    class PreProcessor(
        ortResult: OrtResult,
        model: NoticeReportModel,
        copyrightGarbage: CopyrightGarbage,
        licenseConfiguration: LicenseConfiguration,
        packageConfigurationProvider: PackageConfigurationProvider
    ) : ScriptRunner() {
        override val preface = """
            import org.ossreviewtoolkit.model.*
            import org.ossreviewtoolkit.model.config.*
            import org.ossreviewtoolkit.model.licenses.*
            import org.ossreviewtoolkit.model.utils.*
            import org.ossreviewtoolkit.spdx.*
            import org.ossreviewtoolkit.utils.*
            import org.ossreviewtoolkit.reporter.reporters.AbstractNoticeReporter.NoticeReportModel

            import java.util.*

            var headers = model.headers
            var headerWithLicenses = model.headerWithLicenses
            var headerWithoutLicenses = model.headerWithoutLicenses
            var findings = model.findings
            var footers = model.footers

        """.trimIndent()

        override val postface = """

            // Output:
            NoticeReportModel(headers, headerWithLicenses, headerWithoutLicenses, findings, footers)
        """.trimIndent()

        init {
            engine.put("ortResult", ortResult)
            engine.put("model", model)
            engine.put("copyrightGarbage", copyrightGarbage)
            engine.put("licenseConfiguration", licenseConfiguration)
            engine.put("packageConfigurationProvider", packageConfigurationProvider)
        }

        override fun run(script: String): NoticeReportModel = super.run(script) as NoticeReportModel
    }

    abstract class NoticeProcessor(protected val input: ReporterInput) {
        abstract fun process(model: NoticeReportModel): List<() -> String>
    }

    override fun generateReport(
        outputStream: OutputStream,
        input: ReporterInput
    ) {
        requireNotNull(input.ortResult.scanner) {
            "The provided ORT result file does not contain a scan result."
        }

        val licenseFindings: Map<Identifier, LicenseFindingsMap> =
            getLicenseFindings(input.ortResult, input.packageConfigurationProvider)

        val model = NoticeReportModel(
            emptyList(),
            DEFAULT_HEADER_WITH_LICENSES,
            DEFAULT_HEADER_WITHOUT_LICENSES,
            licenseFindings,
            emptyList()
        )

        val preProcessedModel = input.preProcessingScript?.let { preProcessingScript ->
            PreProcessor(
                input.ortResult,
                model,
                input.copyrightGarbage,
                input.licenseConfiguration,
                input.packageConfigurationProvider
            ).run(preProcessingScript)
        } ?: model

        val processor = createProcessor(input)

        val notices = processor.process(preProcessedModel)

        outputStream.bufferedWriter().use { writer ->
            notices.forEach {
                writer.write(it())
            }
        }
    }

    private fun getLicenseFindings(
        ortResult: OrtResult,
        packageConfigurationProvider: PackageConfigurationProvider
    ): Map<Identifier, LicenseFindingsMap> {
        val concludedLicenses = ortResult.collectConcludedLicenses(omitExcluded = true)
        val declaredLicenses = ortResult.collectDeclaredLicenses(omitExcluded = true)

        val detectedLicenses = ortResult.collectLicenseFindings(packageConfigurationProvider, omitExcluded = true)
            .mapValues { (_, findings) ->
                findings.filter { it.value.isEmpty() }.keys.associate { licenseFindings ->
                    Pair(licenseFindings.license, licenseFindings.copyrights.map { it.statement }.toMutableSet())
                }.toSortedMap()
            }.toMutableMap()

        return (concludedLicenses.keys + declaredLicenses.keys + detectedLicenses.keys).associateWith { id ->
            val licenseFindingsMap = sortedMapOf<String, MutableSet<String>>()
            if (concludedLicenses.containsKey(id)) {
                concludedLicenses.getValue(id).forEach { license ->
                    licenseFindingsMap[license] = detectedLicenses[id]?.get(license) ?: mutableSetOf()
                }
            } else {
                declaredLicenses.getValue(id).forEach { licenseFindingsMap[it] = mutableSetOf() }
                detectedLicenses.getValue(id).forEach { licenseFindingsMap[it.key] = it.value }
            }

            licenseFindingsMap
        }
    }

    abstract fun createProcessor(input: ReporterInput): NoticeProcessor
}
