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

import com.fasterxml.jackson.annotation.JsonInclude

import java.time.Instant

import org.ossreviewtoolkit.utils.ort.Environment

/**
 * Metadata about the ORT run itself.
 */
data class Metadata(
    /**
     * The time the Advisor started.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val advisorStartTime: Instant,

    /**
     * The time the Advisor ended.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val advisorEndTime: Instant,

    /**
     * The [Environment] in which the advisor was executed.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val advisorEnvironment: Environment,

    /**
     * The time the Analyzer started.
     */
    val analyzerStartTime: Instant,

    /**
     * The time the Analyzer ended.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val analyzerEndTime: Instant,

    /**
     * The [Environment] in which the analyzer was executed.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val analyzerEnvironment: Environment,

    /**
     * The time the Evaluator started.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val evaluatorStartTime: Instant,

    /**
     * The time the Evaluator ended.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val evaluatorEndTime: Instant,

    /**
     * The [Environment] in which the evaluator was executed.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val evaluatorEnvironment: Environment,

    /**
     * The time the Evaluator started.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val scannerStartTime: Instant,

    /**
     * The time the Evaluator ended.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val scannerEndTime: Instant,

    /**
     * The [Environment] in which the scanner was executed.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val scannerEnvironment: Environment
)
