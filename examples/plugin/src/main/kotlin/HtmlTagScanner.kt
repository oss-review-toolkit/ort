/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.examples.plugin

import com.here.ort.model.Provenance
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.ScannerDetails
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.scanner.AbstractScannerFactory
import com.here.ort.scanner.LocalScanner

import java.io.File
import java.time.Instant

import org.jsoup.Jsoup

class HtmlTagScanner(config: ScannerConfiguration) : LocalScanner(config) {
    class Factory : AbstractScannerFactory<HtmlTagScanner>() {
        override fun create(config: ScannerConfiguration) = HtmlTagScanner(config)
    }

    override val resultFileExt = "json"
    override val scannerExe = "" // Not using a command line tool.
    override val scannerVersion = "1.0"

    override fun getConfiguration() = ""

    override fun getVersion(dir: File) = scannerVersion

    override fun scanPath(scannerDetails: ScannerDetails, path: File, provenance: Provenance, resultsFile: File)
            : ScanResult {
        val startTime = Instant.now()

        val htmlTags = mutableMapOf<String, Int>()

        path.walk().forEach { file ->
            try {
                val doc = Jsoup.parse(file, "UTF-8")
                val elements = doc.select("*")
                elements.forEach { element ->
                    htmlTags[element.tagName()] = htmlTags.getOrDefault(element.tagName(), 0) + 1
                }
            } catch(e: Exception) {
                // Do nothing if the file cannot be parsed as HTML.
            }
        }

        val jsonResult = jsonMapper.writeValueAsString(htmlTags)
        resultsFile.writeText(jsonResult)

        val endTime = Instant.now()

        val summary = ScanSummary(
                startTime = startTime,
                endTime = endTime,
                fileCount = path.walk().count(),
                licenseFindings = sortedSetOf(),
                errors = listOf(),
                data = mapOf("htmlTags" to htmlTags)
        )

        return ScanResult(provenance, scannerDetails, summary, jsonMapper.readTree(jsonResult))
    }
}
