/*
 * Copyright (C) 2021 Porsche AG
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

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration

/**
 * A [Scanner] that operates on a complete [OrtResult]
 */
abstract class OrtResultScanner(
    scannerName: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : Scanner(scannerName, scannerConfig, downloaderConfig) {

    /**
     * This scanner operates on the level of a complete [OrtResult].
     *
     * Using it for package scanning is neither wanted nor supported and must be considered as an error.
     */
    final override suspend fun scanPackages(
        packages: Set<org.ossreviewtoolkit.model.Package>,
        labels: Map<String, String>
    ): Map<org.ossreviewtoolkit.model.Package, List<ScanResult>> =
        throw NotImplementedError("Not implemented on purpose")

    /**
     * Process a complete [OrtResult] and manage any scanning activities on projects and packages
     * autonomous without any orchestration from ORT
     */
    abstract suspend fun scanOrtResult(
        ortResult: OrtResult,
        labels: Map<String, String>
    ): Map<Package, List<ScanResult>>
}
