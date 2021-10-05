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

import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolution

/**
 * An interface to provide resolutions for [OrtIssue]s, [RuleViolation]s and [Vulnerability]s .
 */
interface ResolutionProvider {
    /**
     * Get all issue resolutions that match [issue].
     */
    fun getIssueResolutionsFor(issue: OrtIssue): List<IssueResolution>

    /**
     * Get all rule violation resolutions that match [violation].
     */
    fun getRuleViolationResolutionsFor(violation: RuleViolation): List<RuleViolationResolution>

    /**
     * Get all vulnerability resolutions that match [vulnerability].
     */
    fun getVulnerabilityResolutionsFor(vulnerability: Vulnerability): List<VulnerabilityResolution>

    /**
     * Get a [Resolutions] object that contains all resolutions which apply to [OrtIssue]s or [RuleViolation]s contained
     * in [ortResult].
     */
    fun getResolutionsFor(ortResult: OrtResult): Resolutions

    /**
     * Return true if there is at least one issue resolution that matches [issue].
     */
    fun isResolved(issue: OrtIssue): Boolean = getIssueResolutionsFor(issue).isNotEmpty()

    /**
     * Return true if there is at least one rule violation resolution that matches [violation].
     */
    fun isResolved(violation: RuleViolation): Boolean = getRuleViolationResolutionsFor(violation).isNotEmpty()

    /**
     * Return true if there is at least one vulnerability resolution that matches [vulnerability].
     */
    fun isResolved(vulnerability: Vulnerability): Boolean = getVulnerabilityResolutionsFor(vulnerability).isNotEmpty()
}
