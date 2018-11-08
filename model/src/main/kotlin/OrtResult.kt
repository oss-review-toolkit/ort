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

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * The common output format for the analyzer and scanner. It contains information about the scanned repository, and the
 * analyzer and scanner will add their result to it.
 */
data class OrtResult(
        /**
         * Information about the repository that was used as input.
         */
        val repository: Repository,

        /**
         * An [AnalyzerRun] containing details about the analyzer that was run using [repository] as input. Can be null
         * if the [repository] was not yet analyzed.
         */
        val analyzer: AnalyzerRun? = null,

        /**
         * A [ScannerRun] containing details about the scanner that was run using the result from [analyzer] as input.
         * Can be null if no scanner was run.
         */
        val scanner: ScannerRun? = null,

        /**
         * An [EvaluatorRun] containing details about the evaluation that was run using the result from [scanner] as
         * input. Can be null if no evaluation was run.
         */
        val evaluator: EvaluatorRun? = null,

        /**
         * A map that holds arbitrary data. Can be used by third-party tools to add custom data to the model.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val data: CustomData = emptyMap()
) {
    /**
     * Conveniently return all detected licenses for the given package [id].
     */
    fun getDetectedLicensesForPackage(id: Identifier) =
            scanner?.results?.scanResults?.find { it.id == id }.getAllDetectedLicenses()
}
