/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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
import com.here.ort.model.config.Resolutions

class DefaultResolutionProvider : ResolutionProvider {
    private var resolutions = Resolutions()

    fun add(resolutions: Resolutions) {
        this.resolutions = this.resolutions.merge(resolutions)
    }

    override fun getErrorResolutionsFor(issue: OrtIssue) = resolutions.errors.filter { it.matches(issue) }

    override fun getRuleViolationResolutionsFor(issue: OrtIssue) =
            resolutions.ruleViolations.filter { it.matches(issue) }

    override fun getResolutionsFor(ortResult: OrtResult): Resolutions {
        val errorResolutions = ortResult.collectErrors().values.flatten().let { errors ->
            resolutions.errors.filter { resolution -> errors.any { resolution.matches(it) } }
        }

        val ruleViolationResolutions = ortResult.evaluator?.errors?.let { errors ->
            resolutions.ruleViolations.filter { resolution -> errors.any { resolution.matches(it) } }
        } ?: emptyList()

        return Resolutions(errorResolutions, ruleViolationResolutions)
    }
}
