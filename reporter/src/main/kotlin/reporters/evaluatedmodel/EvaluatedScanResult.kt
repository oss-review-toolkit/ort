/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.evaluatedmodel

import com.fasterxml.jackson.annotation.JsonInclude

import java.time.Instant

import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails

/**
 * The evaluated form of a [ScanSummary] used by the [EvaluatedModel]. The findings are stored directly in
 * [EvaluatedPackage].
 */
data class EvaluatedScanResult(
    val provenance: Provenance,
    val scanner: ScannerDetails,
    val startTime: Instant,
    val endTime: Instant,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val packageVerificationCode: String,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val issues: List<EvaluatedOrtIssue>
)
