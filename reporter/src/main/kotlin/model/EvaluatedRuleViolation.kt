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

import com.fasterxml.jackson.annotation.JsonInclude

import com.here.ort.model.LicenseSource
import com.here.ort.model.Severity
import com.here.ort.model.config.RuleViolationResolution

data class EvaluatedRuleViolation(
    val rule: String,
    val pkg: EvaluatedPackage,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val license: LicenseId?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val licenseSource: LicenseSource?,
    val severity: Severity,
    val message: String,
    val howToFix: String,
    val resolutions: List<RuleViolationResolution>
)
