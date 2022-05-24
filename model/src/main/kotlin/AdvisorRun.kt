/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.utils.ort.Environment

/**
 * The summary of a single run of the advisor.
 */
data class AdvisorRun(
    /**
     * The [Instant] the advisor was started.
     */
    val startTime: Instant,

    /**
     * The [Instant] the advisor has finished.
     */
    val endTime: Instant,

    /**
     * The [Environment] in which the advisor was executed.
     */
    val environment: Environment,

    /**
     * The [AdvisorConfiguration] used for this run.
     */
    val config: AdvisorConfiguration,

    /**
     * The result of the [AdvisorRun].
     */
    val results: AdvisorRecord
)
