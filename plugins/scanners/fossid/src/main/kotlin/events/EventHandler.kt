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

import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.scanner.ScanContext

/**
 * This interface is used to mark classes as event handlers, i.e. classes that react on different events in the FossID
 * scanner run.
 */
interface EventHandler {
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
}
