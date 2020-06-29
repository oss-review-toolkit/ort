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

package org.ossreviewtoolkit.reporter.reporters

import java.io.File
import java.nio.file.Path

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFindingsMap
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.clean
import org.ossreviewtoolkit.model.merge
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.CopyrightStatementsProcessor
import org.ossreviewtoolkit.utils.FileMatcher
import org.ossreviewtoolkit.utils.LICENSE_FILENAMES
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.logOnce
import org.ossreviewtoolkit.utils.ortDataDirectory
import org.ossreviewtoolkit.utils.storage.FileArchiver
import org.ossreviewtoolkit.utils.storage.LocalFileStorage

/**
 * Creates a notice file containing the licenses for all non-excluded projects and packages, listed by their identifier.
 * Detected copyrights are listed next to the licenses.
 *
 * If a [FileArchiver] is configured for the scanner the reporter will try to download any archived license files and
 * include them in the notice file. Licenses detected in those license files will not be listed again, instead all
 * copyrights associated to those licenses will be listed below the license file's content.
 */
class NoticeByPackageReporter : AbstractNoticeReporter() {
    override val reporterName = "NoticeByPackage"
    override val noticeFilename = "NOTICE_BY_PACKAGE"

    override fun createProcessor(input: ReporterInput): NoticeProcessor = NoticeByPackageProcessor(input)
}

class NoticeByPackageProcessor(input: ReporterInput) : AbstractNoticeReporter.NoticeProcessor(input) {
    companion object {
        private val DEFAULT_ARCHIVE_DIR by lazy { ortDataDirectory.resolve("scanner/archive") }
        private const val LICENSE_SEPARATOR = "\n  --\n\n"
    }

    override fun process(model: AbstractNoticeReporter.NoticeReportModel): List<() -> String> =
        mutableListOf<() -> String>().apply {
            add { model.headers.joinToString(AbstractNoticeReporter.NOTICE_SEPARATOR) }

            if (model.headers.isNotEmpty()) {
                add { AbstractNoticeReporter.NOTICE_SEPARATOR }
            }

            val projectIds = input.ortResult.getProjects().map { it.id }
            val projectFindings = model.findings
                .filter { (id, licenseFindingsMap) ->
                    id in projectIds && licenseFindingsMap.isNotEmpty()
                }
            val packageFindings = model.findings
                .filterNot { (id, _) -> id in projectIds }

            addProjectFindings(projectFindings)

            if (packageFindings.isNotEmpty() && projectFindings.isNotEmpty()) {
                add { AbstractNoticeReporter.NOTICE_SEPARATOR }
            }

            if (packageFindings.isEmpty()) {
                add { model.headerWithoutLicenses }
            } else {
                add { model.headerWithLicenses }

                addPackageFindings(packageFindings)
            }

            model.footers.forEach { footer ->
                add { AbstractNoticeReporter.NOTICE_SEPARATOR }
                add { footer }
            }
        }

    private fun MutableList<() -> String>.addProjectFindings(findings: Map<Identifier, LicenseFindingsMap>) {
        val processedFindings = findings.values
            .merge()
            .clean(input.copyrightGarbage)
            .filter { (license, _) ->
                input.licenseTextProvider.hasLicenseText(license).also {
                    if (!it) {
                        NoticeByPackageProcessor.logOnce(Level.WARN) {
                            "No license text found for license '$license', it will be omitted from the report."
                        }
                    }
                }
            }.toSortedMap()

        if (processedFindings.isNotEmpty()) {
            add { "This software includes external packages and source code.\n" }
            add { "The applicable license information is listed below:\n" }
        }

        add { AbstractNoticeReporter.NOTICE_SEPARATOR }

        addProcessedFindings(processedFindings)
    }

    private fun MutableList<() -> String>.addPackageFindings(findings: Map<Identifier, LicenseFindingsMap>) {
        val archiver = input.ortConfig.scanner?.archive?.createFileArchiver() ?: FileArchiver(
            LICENSE_FILENAMES,
            LocalFileStorage(DEFAULT_ARCHIVE_DIR)
        )

        findings.forEach { (id, licenseFindingsMap) ->
            add { AbstractNoticeReporter.NOTICE_SEPARATOR }

            add { "Package: " }
            if (id.namespace.isNotBlank()) {
                add { "${id.namespace}:" }
            }
            add { "${id.name}:${id.version}\n\n" }

            val scanResult = input.ortResult.getScanResultsForId(id).firstOrNull()
            val archiveDir = createTempDir(ORT_NAME, "notice").also { it.deleteOnExit() }

            val licenseFileFindings = scanResult?.let {
                val path = "${id.toPath()}/${scanResult.provenance.hash()}"
                archiver.unarchive(archiveDir, path).takeIf { it }?.let {
                    getFindingsForLicenseFiles(scanResult, archiveDir, archiver.patterns, licenseFindingsMap)
                }
            }.orEmpty()

            addLicenseFileFindings(licenseFileFindings, archiveDir)

            val licensesInNoticeFiles = licenseFileFindings.values.map { it.keys }.flatten().toSet()
            val processedFindings = licenseFindingsMap
                .clean(input.copyrightGarbage)
                .filter { (license, _) -> license !in licensesInNoticeFiles }
                .filter { (license, _) ->
                    input.licenseTextProvider.hasLicenseText(license).also {
                        if (!it) {
                            NoticeByPackageProcessor.logOnce(Level.WARN) {
                                "No license text found for license '$license', it will be omitted from the report."
                            }
                        }
                    }
                }.toSortedMap()

            if (processedFindings.isNotEmpty()) {
                if (licenseFileFindings.isNotEmpty()) {
                    add { LICENSE_SEPARATOR }
                }

                add { "The following copyrights and licenses were found in the source code of this package:\n\n" }
            }

            addProcessedFindings(processedFindings)

            if (licenseFileFindings.isEmpty() && processedFindings.isEmpty()) {
                NoticeByPackageProcessor.log.error {
                    "No license information was added for package ${id.toCoordinates()}."
                }
            }
        }
    }

    private fun MutableList<() -> String>.addProcessedFindings(processedFindings: LicenseFindingsMap) {
        processedFindings.entries.forEachIndexed { index, (license, copyrights) ->
            input.licenseTextProvider.getLicenseTextReader(license)?.let { licenseTextReader ->
                copyrights.forEach { copyright ->
                    add { "$copyright\n" }
                }
                if (copyrights.isNotEmpty()) {
                    add { "\n" }
                }

                add(licenseTextReader)

                if (index < processedFindings.size - 1) {
                    add { LICENSE_SEPARATOR }
                }
            }
        }
    }

    private fun MutableList<() -> String>.addLicenseFileFindings(
        licenseFileFindings: Map<String, LicenseFindingsMap>,
        archiveDir: File
    ) {
        licenseFileFindings.forEach { (file, findings) ->
            add { "This package contains the file $file with the following contents:\n\n" }
            add { "${archiveDir.resolve(file).readText()}\n" }
            val allCopyrights = findings.values.flatten().toSet()
            val processedCopyrights = CopyrightStatementsProcessor().process(allCopyrights).getAllStatements()
            if (processedCopyrights.isNotEmpty()) {
                add { "The following copyright holder information relates to the license(s) above:\n\n" }
            }
            processedCopyrights.forEach { copyright ->
                add { "$copyright\n" }
            }
        }
    }

    /**
     * Get the license and copyright findings from [scanResult] for all license files in [archiveDir].
     */
    private fun getFindingsForLicenseFiles(
        scanResult: ScanResult,
        archiveDir: File,
        licenseFilePatterns: List<String>,
        licenseFindingsMap: LicenseFindingsMap
    ): MutableMap<String, LicenseFindingsMap> {
        val matcher = FileMatcher(licenseFilePatterns)
        val licenseFiles = mutableMapOf<String, LicenseFindingsMap>()

        archiveDir.walk().forEach { file ->
            val relativePath = archiveDir.toPath().relativize(file.toPath())
            val relativePathString = relativePath.toString()
            if (matcher.matches(relativePathString)) {
                licenseFiles[relativePathString] =
                    getFindingsForLicenseFile(scanResult, relativePath, licenseFindingsMap)
            }
        }

        return licenseFiles
    }

    private fun getFindingsForLicenseFile(
        scanResult: ScanResult,
        path: Path,
        licenseFindingsMap: LicenseFindingsMap
    ): LicenseFindingsMap {
        val licenses = findLicensesForFile(scanResult, path)
        val copyrightsInFile = findCopyrightsForFile(scanResult, path)

        return licenses.associateWith { license ->
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
            it.license.toString()
        }
    }
}
