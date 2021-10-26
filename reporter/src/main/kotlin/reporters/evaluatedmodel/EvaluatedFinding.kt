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

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.config.PathExclude

/**
 * The evaluated form of a [LicenseFinding] used by the [EvaluatedModel].
 */
data class EvaluatedFinding(
    val type: EvaluatedFindingType,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val license: LicenseId?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val copyright: CopyrightStatement?,
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val scanResult: EvaluatedScanResult,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val pathExcludes: List<PathExclude>
)
