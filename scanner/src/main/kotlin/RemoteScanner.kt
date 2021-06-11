/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner

import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration

/**
 * Abstraction for a [Scanner] that runs on a remote host.
 */
abstract class RemoteScanner(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : Scanner(name, scannerConfig, downloaderConfig) {
    /**
     * The version of the scanner, or an empty string if not applicable.
     */
    abstract val version: String

    /**
     * The configuration used by the scanner (this could also be the URL of a specially configured instance), or an
     * empty string if not applicable.
     */
    abstract val configuration: String

    /**
     * Return the [ScannerDetails] of this [RemoteScanner].
     */
    val details by lazy { ScannerDetails(scannerName, version, configuration) }
}
