/*
 * Copyright (C) 2021 Bosch.IO GmbH
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import org.ossreviewtoolkit.model.Finding
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.config.Resolutions

/**
 * A provider of previously added resolutions for [OrtIssue]s and [RuleViolation]s.
 */
class DefaultResolutionProvider : ResolutionProvider {
    private var resolutions = Resolutions()

    /**
     * Add [other] resolutions that get merged with the existing resolutions.
     */
    fun add(other: Resolutions) = apply { resolutions = resolutions.merge(other) }

    override fun getIssueResolutionsFor(issue: OrtIssue) = resolutions.issues.filter { it.matches(issue) }

    override fun getRuleViolationResolutionsFor(violation: RuleViolation) =
        resolutions.ruleViolations.filter { it.matches(violation) }

    override fun getVulnerabilityResolutionsFor(finding: Finding) =
        resolutions.vulnerabilities.filter { it.matches(finding) }

    override fun getResolutionsFor(ortResult: OrtResult): Resolutions {
        val issueResolutions = ortResult.collectIssues().values.flatten().let { issues ->
            resolutions.issues.filter { resolution -> issues.any { resolution.matches(it) } }
        }

        val ruleViolationResolutions = ortResult.evaluator?.violations?.let { violations ->
            resolutions.ruleViolations.filter { resolution -> violations.any { resolution.matches(it) } }
        }.orEmpty()

        return Resolutions(issueResolutions, ruleViolationResolutions)
    }
}
