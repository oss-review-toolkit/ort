/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonProperty

import java.util.SortedSet

/**
 * A record of a single run of the scanner tool, containing the input and the scan results for all scanned packages.
 */
data class ScanRecord(
        /**
         * The [AnalyzerResult] that was used as input for the scanner.
         */
        @JsonProperty("analyzer_result")
        val analyzerResult: AnalyzerResult,

        /**
         * The scanned and ignored [Scope]s for each scanned [Project] by id.
         */
        @JsonProperty("scanned_scopes")
        val scannedScopes: SortedSet<ProjectScanScopes>,

        /**
         * The [ScanResult]s for all [Package]s.
         */
        @JsonProperty("scan_results")
        val scanResults: SortedSet<ScanResultContainer>,

        /**
         * The [CacheStatistics] for the scan results cache.
         */
        @JsonProperty("cache_stats")
        val cacheStats: CacheStatistics
) : CustomData()
