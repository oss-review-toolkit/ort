/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.reporters.evaluatedmodel

import java.time.Instant

import org.ossreviewtoolkit.utils.ort.Environment

/**
 * Metadata about the ORT tool runs.
 */
data class ToolsMetadata(
    /**
     * The metadata for the analyzer run.
     */
    val analyzer: Run?,

    /**
     * The metadata for the scanner run.
     */
    val scanner: Run?,

    /**
     * The metadata for the advisor run.
     */
    val advisor: Run?,

    /**
     * The metadata for the evaluator run.
     */
    val evaluator: Run?
) {
    data class Run(
        /**
         * The time the run has started.
         */
        val startTime: Instant,

        /**
         * The time the run has finished.
         */
        val endTime: Instant,

        /**
         * The [Environment] in which the run was executed.
         */
        val environment: Environment
    )
}
