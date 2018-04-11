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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode

import java.time.Instant
import java.util.SortedSet

/**
 * A list of [ScanResult]s for the package identified by [id].
 */
data class ScanResults(
        /**
         * The [Identifier] of the package these [results] belong to.
         */
        val id: Identifier,

        /**
         * The list of [ScanResult]s.
         */
        val results: List<ScanResult>
)

/**
 * The result of a single scan of a package.
 */
data class ScanResult(
        /**
         * Provenance information about the scanned source code.
         */
        val provenance: Provenance,

        /**
         * Details about the used scanner.
         */
        val scanner: ScannerSpecification,

        /**
         * A summary of the scan results.
         */
        val summary: ScanSummary,

        /**
         * The raw output of the scanner.
         */
        val rawResult: JsonNode
)

/**
 * Provenance information about the scanned source code.
 */
data class Provenance(
        /**
         * The time when the source code was downloaded.
         */
        val downloadTime: Instant,

        /**
         * The source artifact that was downloaded, or null.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val sourceArtifact: RemoteArtifact? = null,

        /**
         * The VCS repository that was downloaded, or null.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val vcsInfo: VcsInfo? = null,

        /**
         * True if a local directory with no provenance information was scanned. Only serialized if the value is true.
         */
        @JsonInclude(JsonInclude.Include.CUSTOM, valueFilter = FalseFilter::class)
        val localScan: Boolean = false
)

/**
 * Details about the used source code scanner.
 */
data class ScannerSpecification(
        /**
         * The name of the scanner.
         */
        val name: String,

        /**
         * The version of the scanner.
         */
        val version: String,

        /**
         * The configuration of the scanner, could be command line arguments for example.
         */
        val configuration: String
)

/**
 * A short summary of the scan results.
 */
data class ScanSummary(
        /**
         * The time when the scan started.
         */
        val startTime: Instant,

        /**
         * The time when the scan finished.
         */
        val endTime: Instant,

        /**
         * The number of scanned files.
         */
        val fileCount: Int,

        /**
         * A list of licenses detected by the scanner.
         */
        val licenses: SortedSet<String>,

        /**
         * A list of errors that occured during the scan.
         */
        val errors: SortedSet<String>
)

/**
 * A filter to be used with [JsonInclude.Include.CUSTOM] to not serialize boolean properties set to false.
 */
private class FalseFilter {
    override fun equals(other: Any?) = other != null && other is Boolean && !other
}
