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

package com.here.ort.reporter.reporters

import com.here.ort.model.LicenseFindingsMap
import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.processStatements
import com.here.ort.model.removeGarbage
import com.here.ort.reporter.LicenseTextProvider
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.utils.ScriptRunner
import com.here.ort.utils.log

import java.io.OutputStream

class NoticeReporter : Reporter() {
    companion object {
        private const val NOTICE_SEPARATOR = "\n----\n\n"
    }

    data class NoticeReport(
        val headers: List<String>,
        val findings: LicenseFindingsMap,
        val footers: List<String>
    )

    class PostProcessor(ortResult: OrtResult, noticeReport: NoticeReport, copyrightGarbage: CopyrightGarbage) :
        ScriptRunner() {
        override val preface = """
            import com.here.ort.model.*
            import com.here.ort.model.config.*
            import com.here.ort.spdx.*
            import com.here.ort.utils.*
            import com.here.ort.reporter.reporters.NoticeReporter.NoticeReport

            import java.util.*

            var headers = noticeReport.headers
            var findings = noticeReport.findings
            var footers = noticeReport.footers

        """.trimIndent()

        override val postface = """

            // Output:
            NoticeReport(headers, findings, footers)
        """.trimIndent()

        init {
            engine.put("ortResult", ortResult)
            engine.put("noticeReport", noticeReport)
            engine.put("copyrightGarbage", copyrightGarbage)
        }

        override fun run(script: String): NoticeReport = super.run(script) as NoticeReport
    }

    override val reporterName = "Notice"
    override val defaultFilename = "NOTICE"

    override fun generateReport(
        outputStream: OutputStream,
        ortResult: OrtResult,
        resolutionProvider: ResolutionProvider,
        licenseTextProvider: LicenseTextProvider,
        copyrightGarbage: CopyrightGarbage,
        postProcessingScript: String?
    ) {
        requireNotNull(ortResult.scanner) {
            "The provided ORT result file does not contain a scan result."
        }

        val licenseFindings = getLicenseFindings(ortResult)

        val header = if (licenseFindings.isEmpty()) {
            "This project neither contains or depends on any third-party software components.\n"
        } else {
            "This project contains or depends on third-party software components pursuant to the following licenses:\n"
        }

        val noticeReport = if (postProcessingScript != null) {
            PostProcessor(
                ortResult,
                NoticeReport(listOf(header), licenseFindings, emptyList()),
                copyrightGarbage
            ).run(postProcessingScript)
        } else {
            val processedFindings = licenseFindings.removeGarbage(copyrightGarbage).processStatements()
            NoticeReport(listOf(header), processedFindings, emptyList())
        }

        outputStream.bufferedWriter().use {
            it.write(generateNotices(noticeReport, licenseTextProvider))
        }
    }

    private fun getLicenseFindings(ortResult: OrtResult): LicenseFindingsMap =
        ortResult
            .collectLicenseFindings(omitExcluded = true)
            .values
            .flatMap { licenseFindingsForPackage -> licenseFindingsForPackage.keys }
            .groupBy({ it.license }, { it.copyrights.map { copyrightFinding -> copyrightFinding.statement } })
            .mapValues { (_, copyrightStatements) -> copyrightStatements.flatten().toMutableSet() }
            .toSortedMap()

    private fun generateNotices(noticeReport: NoticeReport, licenseTextProvider: LicenseTextProvider) =
        buildString {
            append(noticeReport.headers.joinToString(NOTICE_SEPARATOR))

            noticeReport.findings.forEach { (license, copyrights) ->
                licenseTextProvider.getLicenseText(license)?.let { licenseText ->
                    append(NOTICE_SEPARATOR)

                    // Note: Do not use appendln() here as that would write out platform-native line endings, but we
                    // want to normalize on Unix-style line endings for consistency.
                    copyrights.forEach { copyright ->
                        append("$copyright\n")
                    }
                    if (copyrights.isNotEmpty()) append("\n")

                    append(licenseText)
                } ?: log.warn {
                    "No license text found for license '$license', it will be omitted from the report."
                }
            }

            noticeReport.footers.forEach { footer ->
                append(NOTICE_SEPARATOR)
                append(footer)
            }
        }
}
