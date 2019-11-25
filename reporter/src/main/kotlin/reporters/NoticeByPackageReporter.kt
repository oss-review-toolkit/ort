/*
 * Copyright (C) 2019 HERE Europe B.V.
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

import com.here.ort.model.Identifier
import com.here.ort.model.LicenseFindingsMap
import com.here.ort.model.OrtResult
import com.here.ort.model.ScanResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.config.OrtConfiguration
import com.here.ort.model.processStatements
import com.here.ort.model.removeGarbage
import com.here.ort.reporter.LicenseTextProvider
import com.here.ort.spdx.LICENSE_FILENAMES
import com.here.ort.utils.CopyrightStatementsProcessor
import com.here.ort.utils.FileMatcher
import com.here.ort.utils.getUserOrtDirectory
import com.here.ort.utils.log
import com.here.ort.utils.storage.FileArchiver
import com.here.ort.utils.storage.LocalFileStorage
import com.here.ort.utils.toHexString

import java.io.File
import java.nio.file.Path
import java.security.MessageDigest

/**
 * This reporter creates a notice files that contains licenses and copyrights listed by package.
 *
 * For each package all license files archived by the scanner plus copyrights detected for licenses contained in those
 * files are added. Additionally, all detected licenses and copyrights not contained in any license file are listed.
 */
class NoticeByPackageReporter : AbstractNoticeReporter() {
    companion object {
        val DEFAULT_ARCHIVE_DIR by lazy { getUserOrtDirectory().resolve("scanner/archive") }
        const val LICENSE_SEPARATOR = "\n  --\n\n"
    }

    override val defaultFilename = "NOTICE_BY_PACKAGE"
    override val reporterName = "NoticeByPackage"

    override fun StringBuilder.appendLicenses(
        ortResult: OrtResult,
        config: OrtConfiguration,
        noticeReport: NoticeReport,
        licenseTextProvider: LicenseTextProvider,
        copyrightGarbage: CopyrightGarbage
    ) {
        val projectIds = ortResult.getProjects().map { it.id }
        val projectFindings = noticeReport.findings
            .filter { (id, _) -> id in projectIds }
            .filter { (_, licenseFindingsMap) -> licenseFindingsMap.isNotEmpty() }
        val packageFindings = noticeReport.findings
            .filterNot { (id, _) -> id in projectIds }

        appendProjectFindings(projectFindings, licenseTextProvider, copyrightGarbage)

        if (packageFindings.isNotEmpty() && projectFindings.isNotEmpty()) {
            append(NOTICE_SEPARATOR)
        }

        appendPackageFindings(packageFindings, ortResult, config, licenseTextProvider, copyrightGarbage)
    }

    private fun StringBuilder.appendProjectFindings(
        findings: Map<Identifier, LicenseFindingsMap>,
        licenseTextProvider: LicenseTextProvider,
        copyrightGarbage: CopyrightGarbage
    ) {
        if (findings.isNotEmpty()) {
            append("This software includes external packages and source code.\n")
            append("The applicable license information is listed below:\n")
        }

        findings.forEach { (_, licenseFindingsMap) ->
            append(NOTICE_SEPARATOR)

            val processedFindings = licenseFindingsMap.removeGarbage(copyrightGarbage).processStatements()
                .filter { (license, _) ->
                    licenseTextProvider.hasLicenseText(license).also {
                        if (!it) {
                            log.warn {
                                "No license text found for license '$license', it will be omitted from the report."
                            }
                        }
                    }
                }.toSortedMap()

            // TODO: Copyrights missing.
            appendProcessedFindings(processedFindings, licenseTextProvider)
        }
    }

    private fun StringBuilder.appendPackageFindings(
        findings: Map<Identifier, LicenseFindingsMap>,
        ortResult: OrtResult,
        config: OrtConfiguration,
        licenseTextProvider: LicenseTextProvider,
        copyrightGarbage: CopyrightGarbage
    ) {
        val archiver = config.scanner?.archive?.createFileArchiver() ?: FileArchiver(
            LICENSE_FILENAMES,
            LocalFileStorage(DEFAULT_ARCHIVE_DIR)
        )

        if (findings.isNotEmpty()) {
            append("This software uses external dependencies.\n")
            append("The applicable license information is listed below:\n")
        }

        findings.forEach { (id, licenseFindingsMap) ->
            append(NOTICE_SEPARATOR)

            append("Package: ")
            if (id.namespace.isNotBlank()) {
                append("${id.namespace}:")
            }
            append("${id.name}:${id.version}\n\n")

            val scanResult = ortResult.getScanResultsForId(id).firstOrNull()
            val archiveDir = createTempDir(prefix = "notice").also { it.deleteOnExit() }

            val licenseFileFindings = if (scanResult != null) {
                val provenanceBytes = scanResult.provenance.toString().toByteArray()
                val provenanceHash = MessageDigest.getInstance("SHA-1").digest(provenanceBytes).toHexString()
                val path = "${id.toPath()}/$provenanceHash"
                if (archiver.unarchive(archiveDir, path)) {
                    getFindingsForLicenseFiles(scanResult, archiveDir, licenseFindingsMap)
                } else {
                    emptyMap()
                }
            } else {
                emptyMap<String, LicenseFindingsMap>()
            }

            appendLicenseFileFindings(licenseFileFindings, archiveDir)

            val licensesInNoticeFiles = licenseFileFindings.values.map { it.keys }.flatten().toSet()
            val processedFindings = licenseFindingsMap.removeGarbage(copyrightGarbage).processStatements()
                .filter { (license, _) -> license !in licensesInNoticeFiles }
                .filter { (license, _) ->
                    licenseTextProvider.hasLicenseText(license).also {
                        if (!it) {
                            log.warn {
                                "No license text found for license '$license', it will be omitted from the report."
                            }
                        }
                    }
                }.toSortedMap()

            if (processedFindings.isNotEmpty()) {
                if (licenseFileFindings.isNotEmpty()) {
                    append(LICENSE_SEPARATOR)
                }

                append("The following copyrights and licenses were found in the source code of this package:\n\n")
            }

            appendProcessedFindings(processedFindings, licenseTextProvider)

            if (licenseFileFindings.isEmpty() && processedFindings.isEmpty()) {
                log.error { "No license information was added for package ${id.toCoordinates()}." }
            }
        }
    }

    private fun StringBuilder.appendProcessedFindings(
        processedFindings: LicenseFindingsMap,
        licenseTextProvider: LicenseTextProvider
    ) {
        processedFindings.entries.forEachIndexed { index, (license, copyrights) ->
            licenseTextProvider.getLicenseText(license)?.let { licenseText ->
                copyrights.forEach { copyright ->
                    append("$copyright\n")
                }
                if (copyrights.isNotEmpty()) append("\n")

                append(licenseText)

                if (index < processedFindings.size - 1) {
                    append(LICENSE_SEPARATOR)
                }
            }
        }
    }

    private fun StringBuilder.appendLicenseFileFindings(
        licenseFileFindings: Map<String, LicenseFindingsMap>,
        archiveDir: File
    ) {
        licenseFileFindings.forEach { (file, findings) ->
            append("This package contains the file $file with the following contents:\n\n")
            append("${archiveDir.resolve(file).readText()}\n")
            val allCopyrights = findings.values.flatten().toSet()
            val processedCopyrights = CopyrightStatementsProcessor().process(allCopyrights).toMutableSet()
            if (processedCopyrights.isNotEmpty()) {
                append("The following copyright holder information relates to the license(s) above:\n\n")
            }
            processedCopyrights.forEach { copyright ->
                append("$copyright\n")
            }
        }
    }

    private fun getFindingsForLicenseFiles(
        scanResult: ScanResult,
        archiveDir: File,
        licenseFindingsMap: LicenseFindingsMap
    ): MutableMap<String, LicenseFindingsMap> {
        val matcher = FileMatcher(LICENSE_FILENAMES)
        val licenseFiles = mutableMapOf<String, LicenseFindingsMap>()

        archiveDir.walkTopDown().forEach { file ->
            val relativePath = archiveDir.toPath().relativize(file.toPath())
            if (matcher.matches(relativePath.toString())) {
                val licenses = findLicensesForFile(scanResult, relativePath)
                val copyrightsInFile = findCopyrightsForFile(scanResult, relativePath)

                val licensesWithCopyrights = licenses.associateWith { license ->
                    // Remove copyrights which are already contained in the license file.
                    val allCopyrights = (licenseFindingsMap[license].orEmpty() + copyrightsInFile).toSet()
                    val processCopyrightsResult = CopyrightStatementsProcessor().process(allCopyrights)
                    val processedCopyrightsNotInLicenseFile =
                        processCopyrightsResult.processedStatements.filterNot { (_, sources) ->
                            copyrightsInFile.any { it in sources }
                        }
                    val unprocessedCopyrightsNotInLicenseFile =
                        processCopyrightsResult.unprocessedStatements - copyrightsInFile

                    (processedCopyrightsNotInLicenseFile.keys + unprocessedCopyrightsNotInLicenseFile).toMutableSet()
                }.toSortedMap()

                licenseFiles[relativePath.toString()] = licensesWithCopyrights
            }
        }

        return licenseFiles
    }

    private fun findCopyrightsForFile(scanResult: ScanResult, relativePath: Path): Set<String> {
        return scanResult.summary.copyrightFindings.filter {
            it.location.path == relativePath.toString()
        }.mapTo(mutableSetOf()) {
            it.statement
        }
    }

    private fun findLicensesForFile(scanResult: ScanResult, relativePath: Path): Set<String> {
        return scanResult.summary.licenseFindings.filter {
            it.location.path == relativePath.toString()
        }.mapTo(mutableSetOf()) {
            it.license
        }
    }
}
