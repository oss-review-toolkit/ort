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

import com.here.ort.model.Identifier
import com.here.ort.model.LicenseFindingsMap
import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.licenses.LicenseConfiguration
import com.here.ort.reporter.LicenseTextProvider
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.utils.ScriptRunner

import java.io.OutputStream

abstract class AbstractNoticeReporter : Reporter {
    companion object {
        const val NOTICE_SEPARATOR = "\n----\n\n"
    }

    data class NoticeReport(
        val headers: List<String>,
        val findings: Map<Identifier, LicenseFindingsMap>,
        val footers: List<String>
    )

    class PostProcessor(
        ortResult: OrtResult,
        noticeReport: NoticeReport,
        copyrightGarbage: CopyrightGarbage,
        licenseConfiguration: LicenseConfiguration
    ) : ScriptRunner() {
        override val preface = """
            import com.here.ort.model.*
            import com.here.ort.model.config.*
            import com.here.ort.model.licenses.*
            import com.here.ort.spdx.*
            import com.here.ort.utils.*
            import com.here.ort.reporter.reporters.AbstractNoticeReporter.NoticeReport

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
            engine.put("licenseConfiguration", licenseConfiguration)
        }

        override fun run(script: String): NoticeReport = super.run(script) as NoticeReport
    }

    override fun generateReport(
        outputStream: OutputStream,
        ortResult: OrtResult,
        resolutionProvider: ResolutionProvider,
        licenseTextProvider: LicenseTextProvider,
        copyrightGarbage: CopyrightGarbage,
        licenseConfiguration: LicenseConfiguration,
        postProcessingScript: String?
    ) {
        requireNotNull(ortResult.scanner) {
            "The provided ORT result file does not contain a scan result."
        }

        val licenseFindings: Map<Identifier, LicenseFindingsMap> = getLicenseFindings(ortResult)

        val header = if (licenseFindings.isEmpty()) {
            "This project neither contains or depends on any third-party software components.\n"
        } else {
            "This project contains or depends on third-party software components pursuant to the following licenses:\n"
        }

        val noticeReport = if (postProcessingScript != null) {
            PostProcessor(
                ortResult,
                NoticeReport(listOf(header), licenseFindings, emptyList()),
                copyrightGarbage,
                licenseConfiguration
            ).run(postProcessingScript)
        } else {
            NoticeReport(listOf(header), licenseFindings, emptyList())
        }

        outputStream.bufferedWriter().use {
            it.write(generateNotices(noticeReport, licenseTextProvider, copyrightGarbage))
        }
    }

    private fun getLicenseFindings(ortResult: OrtResult): Map<Identifier, LicenseFindingsMap> =
        ortResult.collectLicenseFindings(omitExcluded = true).mapValues { (_, findings) ->
            findings.filter { it.value.isEmpty() }.keys.associate { licenseFindings ->
                Pair(licenseFindings.license, licenseFindings.copyrights.map { it.statement }.toMutableSet())
            }.toSortedMap()
        }

    private fun generateNotices(
        noticeReport: NoticeReport,
        licenseTextProvider: LicenseTextProvider,
        copyrightGarbage: CopyrightGarbage
    ) =
        buildString {
            append(noticeReport.headers.joinToString(NOTICE_SEPARATOR))

            appendLicenses(noticeReport, licenseTextProvider, copyrightGarbage)

            noticeReport.footers.forEach { footer ->
                append(NOTICE_SEPARATOR)
                append(footer)
            }
        }

    abstract fun StringBuilder.appendLicenses(
        noticeReport: NoticeReport,
        licenseTextProvider: LicenseTextProvider,
        copyrightGarbage: CopyrightGarbage
    )
}
