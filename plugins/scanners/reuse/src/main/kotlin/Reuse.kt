/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.scanners.reuse

import java.io.File
import java.time.Instant

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.scanner.LocalPathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdxdocument.SpdxTagValueParser

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

/**
 * The command line interface for the REUSE tool.
 */
object ReuseCommand : CommandLineTool {
    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "reuse.exe" else "reuse").joinToString(File.separator)

    override fun transformVersion(output: String): String =
        // The version string is "reuse, version X.Y.Z"
        output.removePrefix("reuse, version ").trim()

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=6.0.0 <7.0.0")
}

/**
 * A scanner plugin that uses the [REUSE](https://reuse.software/) tool to detect license and copyright information
 * in source code projects that follow the REUSE specification.
 *
 * REUSE is a set of best practices to make licensing easy for humans and machines. This scanner runs `reuse spdx`
 * to generate an SPDX bill of materials and parses it to extract license and copyright findings.
 *
 * Note: This plugin invokes the REUSE Tool (GPL-3.0-or-later) as an external scanner. REUSE is not part of ORT
 * itself. See https://codeberg.org/fsfe/reuse-tool for the REUSE Tool source code.
 */
@OrtPlugin(
    displayName = "REUSE",
    description = "REUSE is a set of best practices to make licensing easy for humans and machines. " +
        "This scanner uses the REUSE tool to detect license and copyright information.",
    factory = ScannerWrapperFactory::class
)
class Reuse(
    override val descriptor: PluginDescriptor = ReuseFactory.descriptor,
    private val config: ReuseConfig
) : LocalPathScannerWrapper() {
    override val configuration by lazy {
        buildList {
            if (config.includeSubmodules) add("--include-submodules")
            if (config.includeMesonSubprojects) add("--include-meson-subprojects")
        }.joinToString(" ")
    }

    override val matcher by lazy { ScannerMatcher.create(details, config) }

    override val version by lazy {
        require(ReuseCommand.isInPath()) {
            "The '${ReuseCommand.command()}' command is not available in the PATH environment."
        }

        ReuseCommand.getVersion()
    }

    override val readFromStorage = config.readFromStorage
    override val writeToStorage = config.writeToStorage

    /** License files found in the LICENSES directory, mapped from relative path to license ID. */
    private var licenseFiles = emptyMap<String, String>()

    override fun runScanner(path: File, context: ScanContext): String {
        // Collect license files from the LICENSES directory.
        licenseFiles = (path / "LICENSES")
            .takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isFile }
            ?.associate { "LICENSES/${it.name}" to it.nameWithoutExtension }
            .orEmpty()

        val commandLineOptions = buildList {
            if (config.includeSubmodules) add("--include-submodules")
            if (config.includeMesonSubprojects) add("--include-meson-subprojects")
            add("--root")
            add(path.absolutePath)
            add("spdx")
        }

        val process = ReuseCommand.run(*commandLineOptions.toTypedArray())

        return with(process) {
            if (stderr.isNotBlank()) logger.debug { stderr }
            if (isError) throw ScanException(errorMessage)

            stdout
        }
    }

    override fun createSummary(result: String, startTime: Instant, endTime: Instant): ScanSummary {
        val spdxDocument = SpdxTagValueParser.parse(result)

        val licenseFindings = mutableSetOf<LicenseFinding>()

        // Add license findings for files in the LICENSES directory.
        licenseFiles.forEach { (path, licenseId) ->
            licenseFindings += LicenseFinding(
                license = licenseId,
                location = TextLocation(path, TextLocation.UNKNOWN_LINE)
            )
        }

        val copyrightFindings = mutableSetOf<CopyrightFinding>()

        spdxDocument.files.forEach { spdxFile ->
            // Add license findings, filtering out NOASSERTION and NONE
            spdxFile.licenseInfoInFiles
                .filterNot { it.equals(SpdxConstants.NOASSERTION, ignoreCase = true) }
                .filterNot { it.equals(SpdxConstants.NONE, ignoreCase = true) }
                .forEach { license ->
                    licenseFindings += LicenseFinding(
                        license = license,
                        location = TextLocation(spdxFile.filename, TextLocation.UNKNOWN_LINE)
                    )
                }

            // Add copyright findings, filtering out NOASSERTION and NONE
            val copyrightText = spdxFile.copyrightText
            if (!copyrightText.equals(SpdxConstants.NOASSERTION, ignoreCase = true) &&
                !copyrightText.equals(SpdxConstants.NONE, ignoreCase = true) &&
                copyrightText.isNotBlank()
            ) {
                splitCopyrightStatements(copyrightText).forEach { statement ->
                    copyrightFindings += CopyrightFinding(
                        statement = statement,
                        location = TextLocation(spdxFile.filename, TextLocation.UNKNOWN_LINE)
                    )
                }
            }
        }

        return ScanSummary(
            startTime = startTime,
            endTime = endTime,
            licenseFindings = licenseFindings,
            copyrightFindings = copyrightFindings
        )
    }

    /**
     * Split a copyright text block into individual copyright statements.
     * Each line that looks like a copyright statement is treated as a separate finding.
     */
    private fun splitCopyrightStatements(copyrightText: String): List<String> =
        copyrightText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
}
