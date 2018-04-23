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

import com.fasterxml.jackson.databind.JsonNode

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
        val scanner: ScannerDetails,

        /**
         * A summary of the scan results.
         */
        val summary: ScanSummary,

        /**
         * The raw output of the scanner.
         */
        val rawResult: JsonNode
)
