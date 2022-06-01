/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners.fossid

import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.summary.Summarizable
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.utils.common.collectMessages

/**
 * A data class to hold FossID raw results.
 */
internal data class RawResults(
    val identifiedFiles: List<IdentifiedFile>,
    val markedAsIdentifiedFiles: List<MarkedAsIdentifiedFile>,
    val listIgnoredFiles: List<IgnoredFile>,
    val listPendingFiles: List<String>
)

/**
 * A data class to hold FossID mapped results.
 */
internal data class FindingsContainer(
    val licenseFindings: MutableList<LicenseFinding>,
    val copyrightFindings: MutableList<CopyrightFinding>
)

/**
 * Map a FossID raw result to sections that can be included in a [org.ossreviewtoolkit.model.ScanSummary].
 */
internal fun <T : Summarizable> List<T>.mapSummary(
    ignoredFiles: Map<String, IgnoredFile>,
    issues: MutableList<OrtIssue>,
    detectedLicenseMapping: Map<String, String>
): FindingsContainer {
    val licenseFindings = mutableListOf<LicenseFinding>()
    val copyrightFindings = mutableListOf<CopyrightFinding>()

    val files = filterNot { it.getFileName() in ignoredFiles }
    files.forEach { summarizable ->
        val summary = summarizable.toSummary()
        val location = TextLocation(summary.path, TextLocation.UNKNOWN_LINE, TextLocation.UNKNOWN_LINE)

        summary.licences.forEach {
            runCatching {
                LicenseFinding.createAndMap(it.identifier, location, detectedLicenseMapping = detectedLicenseMapping)
            }.onSuccess { licenseFinding ->
                licenseFindings += licenseFinding.copy(license = licenseFinding.license.normalize())
            }.onFailure { spdxException ->
                issues += createAndLogIssue(
                    source = "FossId",
                    message = "Failed to parse license '${it.identifier}' as an SPDX expression: " +
                            spdxException.collectMessages()
                )
            }
        }

        summarizable.getCopyright().let {
            if (it.isNotEmpty()) {
                copyrightFindings += CopyrightFinding(it, location)
            }
        }
    }

    return FindingsContainer(
        licenseFindings = licenseFindings,
        copyrightFindings = copyrightFindings
    )
}
