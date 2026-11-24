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

import java.time.Instant

/** A summary of the health of a package as a whole, as determined by a single advisor. */
data class HealthSummary(
    /** The overall score of the package, described like any other [HealthMetric]. */
    val overallScore: HealthMetric? = null,

    /** The time when the provider analyzed the repository of the package. */
    val analyzedAt: Instant? = null,

    /** The revision of the repository of the package that the provider analyzed. */
    val analyzedRevision: String? = null,

    /** The version of the tool that the provider used for the analysis. */
    val toolVersion: String? = null
)
