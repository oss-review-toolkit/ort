/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.reporter.reporters.fossid

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.generateReport
import org.ossreviewtoolkit.clients.fossid.model.report.ReportType
import org.ossreviewtoolkit.clients.fossid.model.report.SelectionType
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.showStackTrace

class FossIdReporter : Reporter {
    companion object {
        /** Name of the configuration property for the server URL. */
        const val SERVER_URL_PROPERTY = "serverUrl"

        /** Name of the configuration property for the API key. */
        const val API_KEY_PROPERTY = "apiKey"

        /** Name of the configuration property for the username. */
        const val USER_PROPERTY = "user"

        /** Name of the configuration property for the report type. Default is [ReportType.HTML_DYNAMIC]. */
        const val REPORT_TYPE_PROPERTY = "reportType"

        /**
         * Name of the configuration property for the selection type.
         * Default is [SelectionType.INCLUDE_ALL_LICENSES].
         */
        const val SELECTION_TYPE_PROPERTY = "selectionType"

        // TODO: The below should be unified with [FossId.SCAN_CODE_KEY], without creating a dependency between scanner
        //       and reporter.
        /**
         * Name of key in [ScanResult.additionalData] containing the scancode.
         */
        const val SCAN_CODE_KEY = "scancode"
    }

    override val reporterName = "FossId"

    override fun generateReport(input: ReporterInput, outputDir: File, options: Map<String, String>): List<File> {
        val serverUrl = requireNotNull(options[SERVER_URL_PROPERTY]) {
            "No FossID server URL configuration found."
        }
        val apiKey = requireNotNull(options[API_KEY_PROPERTY]) {
            "No FossID API Key configuration found."
        }
        val user = requireNotNull(options[USER_PROPERTY]) {
            "No FossID User configuration found."
        }
        val reportType = options[REPORT_TYPE_PROPERTY]?.let {
            runCatching {
                ReportType.valueOf(it)
            }.getOrNull()
        } ?: ReportType.HTML_DYNAMIC

        val selectionType = options[SELECTION_TYPE_PROPERTY]?.let {
            runCatching {
                SelectionType.valueOf(it)
            }.getOrNull()
        } ?: SelectionType.INCLUDE_ALL_LICENSES

        val service = FossIdRestService.createService(serverUrl)

        return runBlocking(Dispatchers.IO) {
            input.ortResult.scanner?.results?.scanResults?.values?.flatten()?.mapNotNull { result ->
                result.additionalData[SCAN_CODE_KEY]?.let { scanCode ->
                    async {
                        this@FossIdReporter.log.info { "Generating report for scan $scanCode." }
                        service.generateReport(user, apiKey, scanCode, reportType, selectionType, outputDir)
                            .onFailure {
                                it.showStackTrace()
                                this@FossIdReporter.log.info {
                                    "Error during report generation: ${it.collectMessages()}."
                                }
                            }.getOrNull()
                    }
                }
            }?.awaitAll()?.filterNotNull().orEmpty()
        }
    }
}
