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

import com.here.ort.model.LicenseFindingsMap
import com.here.ort.model.OrtResult

import java.util.SortedSet

class NoticeReporter : AbstractNoticeReporter() {
    override val noticeFileName = "NOTICE"

    override fun getLicenseFindings(ortResult: OrtResult): LicenseFindingsMap {
        val excludes = ortResult.repository.config.excludes
        val analyzerResult = ortResult.analyzer!!.result
        val scanRecord = ortResult.scanner!!.results

        val licenseFindings = sortedMapOf<String, SortedSet<String>>()

        scanRecord.scanResults.forEach { container ->
            if (excludes?.isExcluded(container.id, analyzerResult) != true) {
                container.results.forEach { result ->
                    result.summary.licenseFindingsMap.forEach { (license, copyrights) ->
                        licenseFindings.getOrPut(license) { sortedSetOf() } += copyrights
                    }
                }
            }
        }

        return licenseFindings
    }
}
