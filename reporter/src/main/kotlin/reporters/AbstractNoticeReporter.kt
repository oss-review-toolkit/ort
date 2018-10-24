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
    override fun generateReport(ortResult: OrtResult, resolutionProvider: ResolutionProvider, outputDir: File): File? {
        require(ortResult.scanner != null) {
            "The provided ORT result file does not contain a scan result."
        }

        val outputFile = File(outputDir, noticeFileName)

        val licenseFindings = getLicenseFindings(ortResult)
        val spdxLicenseFindings = mapSpdxLicenses(licenseFindings)

        val findingsIterator = spdxLicenseFindings.filterNot { (license, _) ->
            // For now, just skip license references for which SPDX has no license text.
            license.startsWith("LicenseRef-")
        }.iterator()

        if (!findingsIterator.hasNext()) {
            log.info { "Not writing a $noticeFileName file as it would be empty." }
            return null
        } else {
            log.info { "Writing $noticeFileName file to '${outputFile.absolutePath}'." }
        }

        // Note: Do not use appendln() here as that would write out platform-native line endings, but we want to
        // normalize on Unix-style line endings for consistency.
        while (findingsIterator.hasNext()) {
            val (license, copyrights) = findingsIterator.next()

            var noticeBuilder = StringBuilder()

            copyrights.forEach { copyright ->
                noticeBuilder.append("$copyright\n")
            }

            if (copyrights.isNotEmpty()) noticeBuilder.append("\n")

            noticeBuilder.append("${getLicenseText(license, true)}\n")

            // Trim lines and remove consecutive blank lines as the license text formatting in SPDX JSON files is
            // broken, see https://github.com/spdx/LicenseListPublisher/issues/30.
            var previousLine = ""
            val trimmedNoticeLines = noticeBuilder.lines().mapNotNull { line ->
                val trimmedLine = line.trim()
                trimmedLine.takeIf { it.isNotBlank() || previousLine.isNotBlank() }
                        .also { previousLine = trimmedLine }
            }

            noticeBuilder = StringBuilder(trimmedNoticeLines.joinToString("\n"))

            // Separate notice rows.
            if (findingsIterator.hasNext()) noticeBuilder.append("\n----\n\n")

            outputFile.appendText(noticeBuilder.toString())
        }

        return outputFile
    }

    private fun mapSpdxLicenses(licenseFindings: SortedMap<String, SortedSet<String>>)
            : SortedMap<String, SortedSet<String>> {
        var result = mapOf<String, SortedSet<String>>()

        licenseFindings.forEach { finding ->
            LicenseMapping.map(finding.key).map { spdxLicense ->
                sortedMapOf(spdxLicense.license to finding.value)
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
