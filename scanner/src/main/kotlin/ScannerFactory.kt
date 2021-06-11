/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import java.util.ServiceLoader

import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration

/**
 * A common interface for use with [ServiceLoader] that all [AbstractScannerFactory] classes need to implement.
 */
interface ScannerFactory {
    /**
     * The name to use to refer to the scanner.
     */
    val scannerName: String

    /**
     * Create a [Scanner] using the specified [scannerConfig] and [downloaderConfig].
     */
    fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration): Scanner
}

/**
 * A generic factory class for a [Scanner].
 */
abstract class AbstractScannerFactory<out T : Scanner>(
    override val scannerName: String
) : ScannerFactory {
    abstract override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration): T

    /**
     * Return the scanner's name here to allow Clikt to display something meaningful when listing the scanners
     * which are enabled by default via their factories.
     */
    override fun toString() = scannerName
}
