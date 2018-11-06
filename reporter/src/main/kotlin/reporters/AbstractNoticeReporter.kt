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

import ch.frankel.slf4k.*

import com.here.ort.model.OrtResult
import com.here.ort.model.config.LicenseMapping
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.utils.log
import com.here.ort.utils.spdx.getLicenseText
import com.here.ort.utils.zipWithDefault

import java.io.File
import java.util.SortedMap
import java.util.SortedSet

abstract class AbstractNoticeReporter : Reporter() {
    override fun generateReport(ortResult: OrtResult, resolutionProvider: ResolutionProvider, outputDir: File): File {
        requireNotNull(ortResult.scanner) {
            "The provided ORT result file does not contain a scan result."
        }

        val outputFile = File(outputDir, noticeFileName)

        val licenseFindings = getLicenseFindings(ortResult)
        val spdxLicenseFindings = mapSpdxLicenses(licenseFindings)

        val findingsIterator = spdxLicenseFindings.filterNot { (license, _) ->
            // For now, just skip license references for which SPDX has no license text.
            license.startsWith("LicenseRef-")
        }.iterator()

        log.info { "Writing $noticeFileName file to '${outputFile.absolutePath}'." }

        if (!findingsIterator.hasNext()) {
            outputFile.appendText("This project neither contains or depends on any third-party software " +
                    "components.\n")
            return outputFile
        }

        outputFile.appendText("This project contains or depends on third-party software components pursuant to the " +
                "following licenses:\n")

        // Note: Do not use appendln() here as that would write out platform-native line endings, but we want to
        // normalize on Unix-style line endings for consistency.
        while (findingsIterator.hasNext()) {
            outputFile.appendText("\n----\n\n")

            val (license, copyrights) = findingsIterator.next()

            val notice = buildString {
                copyrights.forEach { copyright ->
                    append("$copyright\n")
                }

                if (copyrights.isNotEmpty()) append("\n")

                val licenseText = getLicenseText(license, true)
                append("$licenseText\n")
            }

            // Trim lines and remove consecutive blank lines as the license text formatting in SPDX JSON files is
            // broken, see https://github.com/spdx/LicenseListPublisher/issues/30.
            var previousLine = ""
            val trimmedNoticeLines = notice.lines().mapNotNull { line ->
                val trimmedLine = line.trim()
                trimmedLine.takeIf { it.isNotBlank() || previousLine.isNotBlank() }
                        .also { previousLine = trimmedLine }
            }

            val trimmedNotice = trimmedNoticeLines.joinToString("\n")
            outputFile.appendText(trimmedNotice)
        }

        return outputFile
    }

    private fun mapSpdxLicenses(licenseFindings: SortedMap<String, SortedSet<String>>)
            : SortedMap<String, SortedSet<String>> {
        var result = mapOf<String, SortedSet<String>>()

        licenseFindings.forEach { finding ->
            LicenseMapping.map(finding.key).map { spdxLicense ->
                sortedMapOf(spdxLicense.id to finding.value)
            }.forEach {
                result = result.zipWithDefault(it, sortedSetOf()) { left, right ->
                    (left + right).toSortedSet()
                }
            }
        }

        return result.toSortedMap()
    }

    abstract val noticeFileName: String

    abstract fun getLicenseFindings(ortResult: OrtResult): SortedMap<String, SortedSet<String>>
}
