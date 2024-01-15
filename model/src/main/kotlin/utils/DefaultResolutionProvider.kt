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

package org.ossreviewtoolkit.model.utils

import java.io.File

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability

/**
 * A [ResolutionProvider] that provides the given [resolutions].
 */
class DefaultResolutionProvider(private val resolutions: Resolutions = Resolutions()) : ResolutionProvider {
    companion object {
        /**
         * Create a [DefaultResolutionProvider] and add the resolutions from the repository configuration of
         * [ortResult] and the [resolutionsFile].
         */
        fun create(ortResult: OrtResult? = null, resolutionsFile: File? = null): DefaultResolutionProvider {
            val resolutionsFromOrtResult = ortResult?.getRepositoryConfigResolutions() ?: Resolutions()
            val resolutionsFromFile = resolutionsFile?.takeIf { it.isFile }?.readValue() ?: Resolutions()

            return DefaultResolutionProvider(resolutionsFromOrtResult.merge(resolutionsFromFile))
        }
    }

    override fun getResolutionsFor(issue: Issue) = resolutions.issues.filter { it.matches(issue) }

    override fun getResolutionsFor(violation: RuleViolation) =
        resolutions.ruleViolations.filter { it.matches(violation) }

    override fun getResolutionsFor(vulnerability: Vulnerability) =
        resolutions.vulnerabilities.filter { it.matches(vulnerability) }
}
