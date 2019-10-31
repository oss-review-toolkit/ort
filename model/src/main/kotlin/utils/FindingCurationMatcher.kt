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

package com.here.ort.model.utils

import com.here.ort.model.LicenseFinding
import com.here.ort.model.config.LicenseFindingCuration
import com.here.ort.spdx.SpdxLicense

import java.nio.file.FileSystems
import java.nio.file.Paths

/**
 * A class for matching and applying [LicenseFindingCuration]s to [LicenseFinding]s.
 */
class FindingCurationMatcher {
    private fun isPathMatching(finding: LicenseFinding, curation: LicenseFindingCuration): Boolean =
        FileSystems.getDefault().getPathMatcher("glob:${curation.path}").let {
            it.matches(Paths.get(finding.location.path))
        }

    private fun isStartLineMatching(finding: LicenseFinding, curation: LicenseFindingCuration): Boolean =
        curation.startLines.isEmpty() || curation.startLines.any { it.equals(finding.location.startLine) }

    private fun isLineCountMatching(finding: LicenseFinding, curation: LicenseFindingCuration): Boolean =
        curation.lineCount == null || curation.lineCount == finding.location.endLine - finding.location.startLine + 1

    private fun isDetectedLicenseMatching(finding: LicenseFinding, curation: LicenseFindingCuration): Boolean =
        curation.detectedLicense == null || curation.detectedLicense == finding.license

    /**
     * Return true if and only if the given curation is applicable to the given finding.
     */
    fun matches(finding: LicenseFinding, curation: LicenseFindingCuration): Boolean =
        isPathMatching(finding, curation) &&
                isStartLineMatching(finding, curation) &&
                isLineCountMatching(finding, curation) &&
                isDetectedLicenseMatching(finding, curation)

    /**
     * Return the curated finding if the given [curation] is applicable to the given [finding] or the given [finding]
     * otherwise. Null is returned if and only if the given curation is applicable and its concluded license equals
     * [SpdxLicense.NONE].
     */
    fun apply(finding: LicenseFinding, curation: LicenseFindingCuration): LicenseFinding? =
        if (!matches(finding, curation)) finding
        else if (curation.concludedLicense == SpdxLicense.NONE) null
        else finding.copy(license = curation.concludedLicense)

    /**
     * Applies the given [curations] to the given [findings]. In case multiple curations match any given finding all
     * curations are applied to the original finding, thus in this case there are multiple curated findings for one
     * finding.
     */
    fun applyAll(
        findings: Collection<LicenseFinding>,
        curations: Collection<LicenseFindingCuration>
    ): List<LicenseFinding> {
        val result = mutableListOf<LicenseFinding>()

        findings.forEach { finding ->
            val matchingCurations = curations.filter { matches(finding, it) }
            if (matchingCurations.isNotEmpty()) {
                result.addAll(matchingCurations.mapNotNull { apply(finding, it) })
            } else {
                result.add(finding)
            }
        }

        return result
    }
}
