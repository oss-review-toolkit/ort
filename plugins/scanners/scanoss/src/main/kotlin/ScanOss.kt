/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.plugins.scanners.scanoss

import com.scanoss.Scanner
import com.scanoss.utils.JsonUtils
import com.scanoss.utils.PackageDetails

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.scanner.PathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerMatcherConfig
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory

@OrtPlugin(
    id = "SCANOSS",
    displayName = "SCANOSS",
    description = "A wrapper for the SCANOSS snippet scanner.",
    factory = ScannerWrapperFactory::class
)
class ScanOss(
    override val descriptor: PluginDescriptor = ScanOssFactory.descriptor,
    config: ScanOssConfig
) : PathScannerWrapper {
    private val scanossBuilder = Scanner.builder()
        // As there is only a single endpoint, the SCANOSS API client expects the path to be part of the API URL.
        .url(config.apiUrl.removeSuffix("/") + "/scan/direct")
        .apiKey(config.apiKey.value)

    override val version: String by lazy {
        // TODO: Find out the best / cheapest way to query the SCANOSS server for its version.
        PackageDetails.getVersion()
    }

    override val configuration = ""

    override val matcher by lazy {
        ScannerMatcher.create(
            details,
            ScannerMatcherConfig(
                config.regScannerName,
                config.minVersion,
                config.maxVersion,
                configuration
            )
        )
    }

    override val readFromStorage = config.readFromStorage

    override val writeToStorage = config.writeToStorage

    override fun scanPath(path: File, context: ScanContext): ScanSummary {
        val startTime = Instant.now()

        // Build the scanner at function level in case any path-specific settings or filters are needed later.
        val scanoss = scanossBuilder.build()

        val rawResults = when {
            path.isFile -> listOf(scanoss.scanFile(path.toString()))
            else -> scanoss.scanFolder(path.toString())
        }

        val results = JsonUtils.toScanFileResults(rawResults)
        val endTime = Instant.now()
        return generateSummary(startTime, endTime, results)
    }
}
