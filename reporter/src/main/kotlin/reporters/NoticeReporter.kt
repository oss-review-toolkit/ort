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

package com.here.ort.reporter.reporters

import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.config.OrtConfiguration
import com.here.ort.model.licenses.LicenseConfiguration
import com.here.ort.model.processStatements
import com.here.ort.model.removeGarbage
import com.here.ort.reporter.LicenseTextProvider
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.utils.log

/**
 * Creates a summary notice file containing all licenses for all non-excluded projects and packages. Each license
 * appears only once and all copyrights associated to this license are listed next to it.
 */
class NoticeReporter : AbstractNoticeReporter() {
    override fun createProcessor(
        ortResult: OrtResult,
        ortConfig: OrtConfiguration,
        resolutionProvider: ResolutionProvider,
        licenseTextProvider: LicenseTextProvider,
        copyrightGarbage: CopyrightGarbage,
        licenseConfiguration: LicenseConfiguration
    ): NoticeProcessor =
        com.here.ort.reporter.reporters.NoticeProcessor(
            ortResult,
            ortConfig,
            resolutionProvider,
            licenseTextProvider,
            copyrightGarbage,
            licenseConfiguration
        )
}

class NoticeProcessor(
    ortResult: OrtResult,
    ortConfig: OrtConfiguration,
    resolutionProvider: ResolutionProvider,
    licenseTextProvider: LicenseTextProvider,
    copyrightGarbage: CopyrightGarbage,
    licenseConfiguration: LicenseConfiguration
) : AbstractNoticeReporter.NoticeProcessor(
    ortResult,
    ortConfig,
    resolutionProvider,
    licenseTextProvider,
    copyrightGarbage,
    licenseConfiguration
) {
    override fun process(noticeReport: AbstractNoticeReporter.NoticeReport): List<() -> String> =
        mutableListOf<() -> String>().apply {
            add { noticeReport.headers.joinToString(AbstractNoticeReporter.NOTICE_SEPARATOR) }

            val mergedFindings = noticeReport.findings.values.takeIf { it.isNotEmpty() }?.reduce { left, right ->
                left.apply {
                    right.forEach { (license, copyrights) ->
                        getOrPut(license) { mutableSetOf() } += copyrights
                    }
                }
            }?.removeGarbage(copyrightGarbage)?.processStatements() ?: sortedMapOf()

            mergedFindings.forEach { (license, copyrights) ->
                licenseTextProvider.getLicenseText(license)?.let { licenseText ->
                    add { AbstractNoticeReporter.NOTICE_SEPARATOR }

                    copyrights.forEach { copyright ->
                        add { "$copyright\n" }
                    }
                    if (copyrights.isNotEmpty()) add { "\n" }

                    add { licenseText }
                } ?: log.warn {
                    "No license text found for license '$license', it will be omitted from the report."
                }
            }

            noticeReport.footers.forEach { footer ->
                add { AbstractNoticeReporter.NOTICE_SEPARATOR }
                add { footer }
            }
        }
}
