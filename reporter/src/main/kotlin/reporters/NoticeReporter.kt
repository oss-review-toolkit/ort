/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.model.ScanRecord
import com.here.ort.utils.spdx.getLicenseText

import java.io.File
import java.util.SortedSet

class NoticeReporter : Reporter {
    override fun generateReport(scanRecord: ScanRecord, outputDir: File) {
        val noticeFile = File(outputDir, "NOTICE")

        val allFindings = sortedMapOf<String, SortedSet<String>>()

        // TODO: Decide whether we want to merge the list of detected licenses with declared licenses (which do not come
        // with a copyright).
        scanRecord.scanResults.forEach { container ->
            container.results.forEach { result ->
                result.summary.licenseFindings.forEach { licenseFinding ->
                    allFindings.getOrPut(licenseFinding.license) { sortedSetOf() } += licenseFinding.copyrights
                }
            }
        }

        val findingsIterator = allFindings.filterNot { (license, _) ->
            // For now, just skip license references for which SPDX has no license text.
            license.startsWith("LicenseRef-")
        }.iterator()

        while (findingsIterator.hasNext()) {
            val (license, copyrights) = findingsIterator.next()

            var noticeBuilder = StringBuilder()

            copyrights.forEach { copyright ->
                noticeBuilder.appendln(copyright)
            }

            if (copyrights.isNotEmpty()) noticeBuilder.appendln()

            noticeBuilder.appendln(getLicenseText(license))

            // Trim lines and remove consecutive blank lines as the license text formatting in SPDX JSON files is
            // broken, see https://github.com/spdx/LicenseListPublisher/issues/30.
            var previousLine = ""
            val trimmedNoticeLines = noticeBuilder.lines().mapNotNull { line ->
                val trimmedLine = line.trim()
                trimmedLine.takeIf { it.isNotBlank() || previousLine.isNotBlank() }
                        .also { previousLine = trimmedLine }
            }

            noticeBuilder = StringBuilder(trimmedNoticeLines.joinToString("\n"))

            // Separate notice entries.
            if (findingsIterator.hasNext()) {
                noticeBuilder.appendln()
                noticeBuilder.appendln("----")
                noticeBuilder.appendln()
            }

            noticeFile.appendText(noticeBuilder.toString())
        }
    }
}
