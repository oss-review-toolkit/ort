/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package com.here.ort.reporter.model

import com.here.ort.model.Provenance
import com.here.ort.model.ScannerDetails

import java.time.Instant

data class EvaluatedScanResult(
    val provenance: Provenance,
    val scanner: ScannerDetails,
    val startTime: Instant,
    val endTime: Instant,
    val fileCount: Int,
    val packageVerificationCode: String,
    val issues: List<EvaluatedOrtIssue>
)
