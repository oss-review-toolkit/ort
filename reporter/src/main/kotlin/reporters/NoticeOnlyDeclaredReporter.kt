/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.reporter.reporters

import com.here.ort.model.OrtResult
import com.here.ort.model.config.LicenseMapping

import java.util.SortedMap
import java.util.SortedSet

class NoticeOnlyDeclaredReporter : AbstractNoticeReporter() {
    override val noticeFileName = "NOTICE_ONLY_DECLARED"

    override fun getLicenseFindings(ortResult: OrtResult): SortedMap<String, SortedSet<String>> {
        val excludes = ortResult.repository.config.excludes
        val analyzerResult = ortResult.analyzer!!.result
        val scanRecord = ortResult.scanner!!.results

        val licenseFindings = sortedMapOf<String, SortedSet<String>>()

        // TODO: Decide whether we want to merge the list of detected licenses with declared licenses (which do not come
        // with a copyright).
        scanRecord.scanResults.forEach { container ->
            if (excludes == null || !excludes.isPackageExcluded(container.id, analyzerResult)) {
                container.results.forEach { result ->
                    result.summary.licenseFindings.forEach { licenseFinding ->
                        licenseFindings.getOrPut(licenseFinding.license) { sortedSetOf() } += licenseFinding.copyrights
                    }
                }
            }
        }

        val allDeclaredLicenses = ortResult.analyzer?.result?.packages?.flatMapTo(sortedSetOf()) { curated ->
            curated.pkg.declaredLicenses
        } ?: sortedSetOf<String>()

        val allDeclaredSpdxLicenses = allDeclaredLicenses
                .flatMap { LicenseMapping.map(it) }
                .mapTo(sortedSetOf()) { it.id }

        return licenseFindings.filterTo(sortedMapOf()) { (license, _) ->
            // Only consider detected licenses that also are declared licenses. We associate detected and declared
            // licenses this way as otherwise we would not know which copyrights to use for which declared license.
            (license in allDeclaredSpdxLicenses)
        }
    }
}
