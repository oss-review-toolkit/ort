/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import java.net.URI

enum class Criticality {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class HealthMetric(
    /** The unique, human-readable name of the health metric. */
    val name: String,

    /** The raw metric score as returned by the provider. */
    val value: Double,

    /** A rating how critical the value is. */
    val criticality: Criticality? = null,

    /** The reason for the score. */
    val reason: String? = null,

    /** Details about the findings. */
    val details: List<String> = emptyList(),

    /** Summary of documentation about the metric. */
    val documentation: String? = null,

    /** Link to the documentation about the metric. */
    val documentationLink: URI? = null
)
