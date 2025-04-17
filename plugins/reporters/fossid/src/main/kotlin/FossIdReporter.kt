/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.fossid

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.generateReport
import org.ossreviewtoolkit.clients.fossid.model.report.ReportType
import org.ossreviewtoolkit.clients.fossid.model.report.SelectionType
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.api.Secret
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.runBlocking
import org.ossreviewtoolkit.utils.ort.showStackTrace

data class FossIdReporterConfig(
    /**
     * The URL of the FossID server to connect to.
     */
    val serverUrl: String,

    /**
     * The API key to use for authentication.
     */
    val apiKey: Secret,

    /**
     * The user to authenticate as.
     */
    val user: Secret,

    /**
     * The type of report to generate. See [ReportType].
     */
    @OrtPluginOption(defaultValue = "XLSX")
    val reportType: String,

    /**
     * The type of selection to use. Allowed values are "INCLUDE_ALL_LICENSES", "INCLUDE_COPYLEFT", "INCLUDE_FOSS", and
     * "INCLUDE_MARKED_LICENSES".
     */
    @OrtPluginOption(defaultValue = "INCLUDE_ALL_LICENSES")
    val selectionType: String
)

@OrtPlugin(
    id = "FossID",
    displayName = "FossID",
    description = "Export reports from FossID.",
    factory = ReporterFactory::class
)
class FossIdReporter(
    override val descriptor: PluginDescriptor = FossIdReporterFactory.descriptor,
    private val config: FossIdReporterConfig
) : Reporter {
    companion object {
        // TODO: The below should be unified with [FossId.SCAN_CODE_KEY], without creating a dependency between scanner
        //       and reporter.
        /**
         * Name of key in [ScanResult.additionalData] containing the scancode.
         */
        const val SCAN_CODE_KEY = "scancode"
    }

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val reportType = ReportType.valueOf(config.reportType)
        val selectionType = SelectionType.valueOf(config.selectionType)

        return runBlocking(Dispatchers.IO.limitedParallelism(20)) {
            val service = FossIdRestService.create(config.serverUrl)
            val scanResults = input.ortResult.getScanResults().values.flatten()
            val scanCodes = scanResults.flatMapTo(mutableSetOf()) {
                it.additionalData[SCAN_CODE_KEY]?.split(',').orEmpty()
            }

            scanCodes.map { scanCode ->
                async {
                    logger.info { "Generating report for scan $scanCode." }

                    service.generateReport(
                        config.user.value,
                        config.apiKey.value,
                        scanCode,
                        reportType,
                        selectionType,
                        outputDir
                    ).onFailure {
                        it.showStackTrace()
                        logger.info {
                            "Error during report generation: ${it.collectMessages()}."
                        }
                    }
                }
            }.awaitAll()
        }
    }
}
