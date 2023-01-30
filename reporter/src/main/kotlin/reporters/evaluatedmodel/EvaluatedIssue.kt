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

package org.ossreviewtoolkit.reporter.reporters.evaluatedmodel

import com.fasterxml.jackson.annotation.JsonIdentityReference
import com.fasterxml.jackson.annotation.JsonInclude

import java.time.Instant

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.IssueResolution

/**
 * The evaluated form of a [Issue] used by the [EvaluatedModel].
 */
data class EvaluatedIssue(
    val timestamp: Instant,
    val type: EvaluatedIssueType,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val source: String,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val message: String,
    val severity: Severity = Severity.ERROR,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val resolutions: List<IssueResolution>,
    @JsonIdentityReference(alwaysAsId = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val pkg: EvaluatedPackage?,
    @JsonIdentityReference(alwaysAsId = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val scanResult: EvaluatedScanResult?,
    @JsonIdentityReference(alwaysAsId = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val path: EvaluatedPackagePath?,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val howToFix: String
)
