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

package com.here.ort.model

import java.util.SortedSet

/**
 * A record of a single run of the scanner tool, containing the input and the scan results for all scanned packages.
 */
data class ScanRecord(
        /**
         * The scanned and ignored [Scope]s for each scanned [Project] by id.
         */
        val scannedScopes: SortedSet<ProjectScanScopes>,

        /**
         * The [ScanResult]s for all [Package]s.
         */
        val scanResults: SortedSet<ScanResultContainer>,

        /**
         * The [CacheStatistics] for the scan results cache.
         */
        val cacheStats: CacheStatistics
) : CustomData() {
    /**
     * True if any of the [scanResults] contain errors.
     */
    @Suppress("UNUSED")
    val hasErrors = scanResults.any { it.results.any { it.summary.errors.isNotEmpty() } }
}
