/*
 * Copyright (C) 2019 HERE Europe B.V.
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

import org.ossreviewtoolkit.model.clean
import org.ossreviewtoolkit.model.merge
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.reporters.AbstractNoticeReporter.NoticeReportModel
import org.ossreviewtoolkit.utils.log

/**
 * Creates a summary notice file containing all licenses for all non-excluded projects and packages. Each license
 * appears only once and all copyrights associated to this license are listed next to it.
 *
 * This reporter supports the following options:
 * - *preProcessingScript*: The path to a Kotlin script to pre-process the [NoticeReportModel] before generating the
 *   notice file.
 */
class NoticeSummaryReporter : AbstractNoticeReporter() {
    override val reporterName = "NoticeSummary"
    override val noticeFilename = "NOTICE_SUMMARY"

    override fun createProcessor(input: ReporterInput): NoticeProcessor = NoticeSummaryProcessor(input)
}

class NoticeSummaryProcessor(input: ReporterInput) : AbstractNoticeReporter.NoticeProcessor(input) {
    override fun process(model: AbstractNoticeReporter.NoticeReportModel): List<() -> String> =
        mutableListOf<() -> String>().apply {
            add { model.headers.joinToString(AbstractNoticeReporter.NOTICE_SEPARATOR) }

            if (model.headers.isNotEmpty()) {
                add { AbstractNoticeReporter.NOTICE_SEPARATOR }
            }

            val mergedFindings = mergeFindings(model)

            if (mergedFindings.isEmpty()) {
                add { model.headerWithoutLicenses }
            } else {
                add { model.headerWithLicenses }

                mergedFindings.forEach { (license, copyrights) ->
                    addLicense(license, copyrights)
                }
            }

            model.footers.forEach { footer ->
                add { AbstractNoticeReporter.NOTICE_SEPARATOR }
                add { footer }
            }
        }

    private fun MutableList<() -> String>.addLicense(license: String, copyrights: Set<String>) {
        input.licenseTextProvider.getLicenseTextReader(license)?.let { licenseTextReader ->
            add { AbstractNoticeReporter.NOTICE_SEPARATOR }

            copyrights.forEach { copyright ->
                add { "$copyright\n" }
            }
            if (copyrights.isNotEmpty()) add { "\n" }

            add(licenseTextReader)
        } ?: this@NoticeSummaryProcessor.log.warn {
            "No license text found for license '$license', it will be omitted from the report."
        }
    }

    private fun mergeFindings(model: AbstractNoticeReporter.NoticeReportModel) =
        model.findings.values.merge().clean(input.copyrightGarbage)
}
