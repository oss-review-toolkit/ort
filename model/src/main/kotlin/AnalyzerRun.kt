/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import java.time.Instant

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.utils.ort.Environment

/**
 * The summary of a single run of the analyzer.
 */
data class AnalyzerRun(
    /**
     * The [Instant] the analyzer was started..
     */
    val startTime: Instant,

    /**
     * The [Instant] the analyzer has finished.
     */
    val endTime: Instant,

    /**
     * The [Environment] in which the analyzer was executed.
     */
    val environment: Environment,

    /**
     * The [AnalyzerConfiguration] used for this run.
     */
    val config: AnalyzerConfiguration,

    /**
     * The result of this run.
     */
    val result: AnalyzerResult
) {
    companion object {
        /**
         * A constant for an [AnalyzerRun] where all properties are empty.
         */
        @JvmField
        val EMPTY = AnalyzerRun(
            startTime = Instant.EPOCH,
            endTime = Instant.EPOCH,
            environment = Environment(),
            config = AnalyzerConfiguration(),
            result = AnalyzerResult.EMPTY
        )
    }
}
