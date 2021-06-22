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
import org.ossreviewtoolkit.model.TextLocation

/**
 * A simple data class to hold FossId raw results.
 */
internal data class RawResults(
    val identifiedFiles: List<IdentifiedFile>,
    val markedAsIdentifiedFiles: List<MarkedAsIdentifiedFile>,
    val listIgnoredFiles: List<IgnoredFile>,
    val listPendingFiles: List<String>
)

/**
 * A simple Triple data class to hold FossId mapped results.
 */
internal data class FindingsContainer(
    val licenseFindings: MutableList<LicenseFinding>,
    val copyrightFindings: MutableList<CopyrightFinding>
)

/**
 * Map a fossId Raw result to sections that can be included in a [org.ossreviewtoolkit.model.ScanSummary].
 */
internal fun <T : Summarizable> List<T>.mapSummary(ignoredFiles: Map<String, IgnoredFile>): FindingsContainer {
    val licenseFindings = mutableListOf<LicenseFinding>()
    val copyrightFindings = mutableListOf<CopyrightFinding>()

    val files = filterNot { ignoredFiles.contains(it.getFileName()) }
    files.forEach { summarizable ->
        val summary = summarizable.toSummary()
        val location = TextLocation(summary.path, -1, -1)

        summary.licences.forEach {
            val finding = LicenseFinding(it.identifier, location)
            licenseFindings += finding
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
