/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.reporter

import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.model.RuleViolation
import com.here.ort.model.config.ErrorResolution
import com.here.ort.model.config.Resolutions
import com.here.ort.model.config.RuleViolationResolution

/**
 * A provider for resolutions of [OrtIssue]s.
 */
interface ResolutionProvider {
    /**
     * Get all error resolutions that match [error].
     */
    fun getErrorResolutionsFor(issue: OrtIssue): List<ErrorResolution>

    /**
     * Get all rule violation resolutions that match [error].
     */
    fun getRuleViolationResolutionsFor(violation: RuleViolation): List<RuleViolationResolution>

    /**
     * Get a [Resolutions] object that contains all resolutions which apply to errors contained in [ortResult].
     */
    fun getResolutionsFor(ortResult: OrtResult): Resolutions
}
