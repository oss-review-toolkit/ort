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
import com.here.ort.model.config.Resolutions

class DefaultResolutionProvider : ResolutionProvider {
    private var resolutions = Resolutions()

    fun add(resolutions: Resolutions) {
        this.resolutions = this.resolutions.merge(resolutions)
    }

    override fun getResolutionsFor(error: OrtIssue) = resolutions.errors.filter { it.matches(error) }

    override fun getEvaluatorResolutionsFor(error: OrtIssue) = resolutions.ruleViolations.filter { it.matches(error) }
}
