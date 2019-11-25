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
import com.here.ort.model.processStatements
import com.here.ort.model.removeGarbage
import com.here.ort.reporter.LicenseTextProvider
import com.here.ort.utils.log

class NoticeReporter : AbstractNoticeReporter() {
    override val defaultFilename = "NOTICE"
    override val reporterName = "Notice"

    override fun StringBuilder.appendLicenses(
        ortResult: OrtResult,
        config: OrtConfiguration,
        noticeReport: NoticeReport,
        licenseTextProvider: LicenseTextProvider,
        copyrightGarbage: CopyrightGarbage
    ) {
        if (noticeReport.findings.isEmpty()) {
            append("This project neither contains nor depends on any third-party software components.\n")
        } else {
            append("This project contains or depends on third-party software components.\n")
            append("The applicable license information is listed below:\n")
        }

        val mergedFindings = noticeReport.findings.values.takeIf { it.isNotEmpty() }?.reduce { left, right ->
            left.apply {
                right.forEach { (license, copyrights) ->
                    getOrPut(license) { mutableSetOf() } += copyrights
                }
            }
        }?.removeGarbage(copyrightGarbage)?.processStatements() ?: sortedMapOf()

        mergedFindings.forEach { (license, copyrights) ->
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
    }
}
