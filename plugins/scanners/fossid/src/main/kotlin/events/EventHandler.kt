/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid.events

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.fossid.FossIdServiceWithVersion
import org.ossreviewtoolkit.clients.fossid.PolymorphicDataResponseBody
import org.ossreviewtoolkit.clients.fossid.model.CreateScanResponse
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.plugins.scanners.fossid.FossIdConfig
import org.ossreviewtoolkit.plugins.scanners.fossid.OrtScanComment
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance

/**
 * This interface is used to mark classes as event handlers, i.e. classes that react on different events in the FossID
 * scanner run.
 */
interface EventHandler {
    companion object {
        /**
         * Return an [EventHandler] based on the given [config], [nestedProvenance] and [service].
         */
        fun getHandler(
            config: FossIdConfig,
            nestedProvenance: NestedProvenance?,
            service: FossIdServiceWithVersion
        ): EventHandler =
            if (config.isArchiveMode && nestedProvenance != null) {
                logger.info {
                    "The FossID scanner is configured to upload an archive to the FossID instance. "
                }

                UploadArchiveHandler(config, service, nestedProvenance)
            } else {
                if (config.isArchiveMode) {
                    logger.warn {
                        "Nested provenance cannot be resolved and upload archive mode is enabled. Falling back to " +
                            "clone repository mode."
                    }
                }

                logger.info {
                    "The FossID scanner is configured to request the FossID instance to clone the source repository. "
                }

                CloneRepositoryHandler(config, service)
            }

        /**
         * Return an [EventHandler] based on the given [config], [nestedProvenance] and [service]. The handler is
         * tailored for the given [existingScan].
         */
        fun getHandler(
            existingScan: Scan,
            config: FossIdConfig,
            nestedProvenance: NestedProvenance?,
            service: FossIdServiceWithVersion
        ): EventHandler {
            // Create a specific handler for the existing scan, based on its configuration, not to the current scanner
            // configuration.
            val configForExistingScan = config.copy(isArchiveMode = existingScan.gitRepoUrl == null)
            return getHandler(configForExistingScan, nestedProvenance, service)
        }
    }

    /**
     * The true if the given [pkg] is valid for the current event handler, false otherwise.
     */
    fun isPackageValid(pkg: Package): Boolean = getPackageInvalidErrorMessage(pkg) == null

    /**
     * Check if the given [pkg] is valid for the current event handler. Return null if the package is valid, the error
     * message to display in the issue otherwise.
     */
    fun getPackageInvalidErrorMessage(pkg: Package): String? = null

    /**
     * Transform the given VCS [url] if required.
     */
    fun transformURL(url: String): String = url

    /**
     * Create a scan in FossID with the given [repositoryUrl], [projectCode], [scanCode], and revision and reference
     * contained in the [comment]. Return the response from FossID or null if the scan could not be created. Note that
     * the [repositoryUrl] may contain the credentials for cloning the repository.
     */
    suspend fun createScan(
        repositoryUrl: String,
        projectCode: String,
        scanCode: String,
        comment: OrtScanComment
    ): PolymorphicDataResponseBody<CreateScanResponse>

    /**
     * Event handler that is called after a scan has been created.
     */
    suspend fun afterScanCreation(
        scanCode: String,
        existingScan: Scan?,
        issues: MutableList<Issue>,
        context: ScanContext
    ) {}

    /**
     * Event handler that is called before a scan has been checked.
     */
    suspend fun beforeCheckScan(scanCode: String) {}

    /**
     * Event handler that is called after a scan has been checked.
     */
    suspend fun afterCheckScan(scanCode: String) {}
}
