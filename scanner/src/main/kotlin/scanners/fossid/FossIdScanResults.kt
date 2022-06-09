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
 * Collection of problematic FossID license expressions. Compiled from FossID's list of licenses. May be updated if
 * FossID adds new licenses.
 */
private val fossIdLicenseMappings = mapOf(
    "BSD (Three Clause License)" to "BSD-3-clause",
    "BSD-Acknowledgment-(Carrot2)" to "LicenseRef-scancode-bsd-ack-carrot2",
    "Freeware-Public-(FPL)" to "LicenseRef-scancode-fpl",
    "MediaInfo(Lib)" to "LicenseRef-scancode-mediainfo-lib",
    "Oracle-Master-Agreement-(OMA)" to "LicenseRef-scancode-oracle-master-agreement",
    "Things-I-Made-(TIM)-Public" to "LicenseRef-scancode-things-i-made-public-license",
    "X11-Style-(Adobe)" to "LicenseRef-scancode-x11-adobe",
    "X11-Style-(Adobe-DEC)" to "LicenseRef-scancode-x11-adobe-dec",
    "X11-Style-(Bitstream-Charter)" to "LicenseRef-scancode-x11-bitstream",
    "X11-Style-(DEC-1)" to "LicenseRef-scancode-x11-dec1",
    "X11-Style-(DEC-2)" to "LicenseRef-scancode-x11-dec2",
    "X11-Style-(DSC-Technologies)" to "LicenseRef-scancode-x11-dsc",
    "X11-Style-(David-R.-Hanson)" to "LicenseRef-scancode-x11-hanson",
    "X11-Style-(Keith-Packard)" to "HPND-sell-variant",
    "X11-Style-(Lucent)" to "LicenseRef-scancode-x11-lucent",
    "X11-Style-(OAR)" to "LicenseRef-scancode-x11-oar",
    "X11-Style-(Open-Group)" to "MIT-open-group",
    "X11-Style-(Quarterdeck)" to "LicenseRef-scancode-x11-quarterdeck",
    "X11-Style-(Realmode)" to "LicenseRef-scancode-x11-realmode",
    "X11-Style-(Silicon-Graphics)" to "LicenseRef-scancode-x11-sg",
    "X11-Style-(Tektronix)" to "LicenseRef-scancode-x11-tektronix",
    "u_Agere-BSD" to "LicenseRef-scancode-agere-bsd",
    "u_BSD-3-Clause-Sun" to "LicenseRef-scancode-bsd-3-clause-sun",
    "u_CC-BY-2.0-UK" to "LicenseRef-scancode-cc-by-2.0-uk",
    "u_GLUT-License" to "LicenseRef-scancode-glut",
    "u_HACOS-1.2" to "LicenseRef-scancode-hacos-1.2",
    "u_Jscheme-License" to "LicenseRef-scancode-jscheme",
    "u_MongoDB-SSPL-1.0" to "SSPL-1.0",
    "u_Philips-Proprietary-Notice-2000" to "LicenseRef-scancode-philips-proprietary-notice-2000",
    "u_Sun-BCL-11-plus-6" to "LicenseRef-scancode-sun-bcl-11-06",
    "u_Sun-Communications-API" to "LicenseRef-scancode-sun-communications-api",
    "u_Sun-EJB-Specification-3.0" to "LicenseRef-scancode-sun-ejb-spec-3.0",
    "u_Sun-Entitlement-3-plus-15" to "LicenseRef-scancode-sun-entitlement-03-15",
    "u_Sun-Entitlement-JAF" to "LicenseRef-scancode-sun-entitlement-jaf",
    "u_Sun-Java-Web-Services-Developer-Pack-1.6" to "Xerox",
    "u_Sun-JavaMail" to "LicenseRef-scancode-sun-javamail",
    "u_Sun-Project-X" to "LicenseRef-scancode-sun-project-x",
    "u_Taligent-JDK-Proprietary-Notice" to "LicenseRef-scancode-taligent-jdk",
    "u_Trolltech-GPL-Exception-1.2" to "LicenseRef-scancode-trolltech-gpl-exception-1.2",
    "u_Vuforia-2013-07-29" to "LicenseRef-scancode-vuforia-2013-07-29",
    "u_Xerox" to "Xerox",
    "u_nexB-EULA-for-SaaS-1.1.0" to "LicenseRef-scancode-nexb-eula-saas-1.1.0",
    "u_nexB-SSLA-1.1.0" to "LicenseRef-scancode-nexb-ssla-1.1.0"
)

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
            val license = fossIdLicenseMappings[it.identifier] ?: it.identifier

            runCatching {
                LicenseFinding.createAndMap(license, location, detectedLicenseMapping = detectedLicenseMapping)
            }.onSuccess { licenseFinding ->
                licenseFindings += licenseFinding.copy(license = licenseFinding.license.normalize())
            }.onFailure { spdxException ->
                issues += createAndLogIssue(
                    source = "FossId",
                    message = "Failed to parse license '$license' as an SPDX expression: " +
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
