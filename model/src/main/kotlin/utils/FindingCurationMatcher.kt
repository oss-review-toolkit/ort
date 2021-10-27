/*
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

import java.nio.file.FileSystems
import java.nio.file.Paths

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.licenses.LicenseFindingCurationResult
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

/**
 * A class for matching and applying [LicenseFindingCuration]s to [LicenseFinding]s.
 */
class FindingCurationMatcher {
    private fun isPathMatching(
        finding: LicenseFinding,
        curation: LicenseFindingCuration,
        relativeFindingPath: String
    ): Boolean =
        FileSystems.getDefault()
            .getPathMatcher("glob:${curation.path}")
            .matches(Paths.get(finding.location.prependPath(relativeFindingPath)))

    private fun isStartLineMatching(finding: LicenseFinding, curation: LicenseFindingCuration): Boolean =
        curation.startLines.isEmpty() || curation.startLines.any { it == finding.location.startLine }

    private fun isLineCountMatching(finding: LicenseFinding, curation: LicenseFindingCuration): Boolean =
        curation.lineCount == null || curation.lineCount == finding.location.endLine - finding.location.startLine + 1

    private fun isDetectedLicenseMatching(finding: LicenseFinding, curation: LicenseFindingCuration): Boolean =
        curation.detectedLicense == null || curation.detectedLicense == finding.license

    /**
     * Return true if and only if the given [curation] is applicable to the given [finding]. The [relativeFindingPath]
     * path is the path from the directory the [curation] is relative to, to the directory the [finding] is relative to.
     */
    fun matches(finding: LicenseFinding, curation: LicenseFindingCuration, relativeFindingPath: String = ""): Boolean =
        isPathMatching(finding, curation, relativeFindingPath) &&
                isStartLineMatching(finding, curation) &&
                isLineCountMatching(finding, curation) &&
                isDetectedLicenseMatching(finding, curation)

    /**
     * Return the curated finding if the given [curation] is applicable to the given [finding] or the given [finding]
     * otherwise. Null is returned if and only if the given curation is applicable and its concluded license equals
     * [SpdxConstants.NONE]. If the finding is relative to a subdirectory of the path that the curations are relative
     * to, this needs to be set in the [relativeFindingPath].
     */
    fun apply(
        finding: LicenseFinding,
        curation: LicenseFindingCuration,
        relativeFindingPath: String = ""
    ): LicenseFinding? =
        if (!matches(finding, curation, relativeFindingPath)) finding
        else if (curation.concludedLicense.toString() == SpdxConstants.NONE) null
        else finding.copy(license = curation.concludedLicense)

    /**
     * Applies the given [curations] to the given [findings]. In case multiple curations match any given finding all
     * curations are applied to the original finding, thus in this case there are multiple curated findings for one
     * finding. If multiple curations lead to the same result, only one of them is contained in the returned map. If the
     * findings are relative to a subdirectory of the path that the curations are relative to, this needs to be set in
     * the [relativeFindingsPath].
     */
    fun applyAll(
        findings: Collection<LicenseFinding>,
        curations: Collection<LicenseFindingCuration>,
        relativeFindingsPath: String = ""
    ): List<LicenseFindingCurationResult> {
        val result = mutableMapOf<LicenseFinding?, MutableList<Pair<LicenseFinding, LicenseFindingCuration>>>()

        findings.forEach { finding ->
            val matchingCurations = curations.filter { matches(finding, it, relativeFindingsPath) }
            if (matchingCurations.isNotEmpty()) {
                matchingCurations.forEach { curation ->
                    val curatedFinding = apply(finding, curation, relativeFindingsPath)
                    result.getOrPut(curatedFinding) { mutableListOf() } += Pair(finding, curation)
                }
            } else {
                result.getOrPut(finding) { mutableListOf() }
            }
        }

        return result.map { (curatedFinding, originalFindings) ->
            LicenseFindingCurationResult(curatedFinding, originalFindings)
        }
    }
}
