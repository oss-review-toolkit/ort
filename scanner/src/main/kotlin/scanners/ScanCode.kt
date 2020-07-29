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

package org.ossreviewtoolkit.scanner.scanners

import com.fasterxml.jackson.databind.JsonNode

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.time.Instant
import java.util.regex.Pattern

import kotlin.math.max

import okhttp3.Request

import okio.buffer
import okio.sink

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.LocalScanner
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.spdx.NON_LICENSE_FILENAMES
import org.ossreviewtoolkit.spdx.SpdxConstants
import org.ossreviewtoolkit.spdx.calculatePackageVerificationCode
import org.ossreviewtoolkit.utils.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.ProcessCapture
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.textValueOrEmpty
import org.ossreviewtoolkit.utils.unpack

/**
 * A wrapper for [ScanCode](https://github.com/nexB/scancode-toolkit).
 *
 * This scanner can be configured in [ScannerConfiguration.options] using the key "ScanCode". It offers the following
 * configuration options:
 *
 * * **"commandLine":** Command line options that modify the result. These are added to the [ScannerDetails] when
 *   looking up results from the [ScanResultsStorage]. Defaults to [DEFAULT_CONFIGURATION_OPTIONS].
 * * **"commandLineNonConfig":** Command line options that do not modify the result and should therefore not be
 *   considered in [getConfiguration], like "--processes". Defaults to [DEFAULT_NON_CONFIGURATION_OPTIONS].
 * * **"debugCommandLine":** Debug command line options that modify the result. Only used if the [log] level is set to
 *   [Level.DEBUG]. Defaults to [DEFAULT_DEBUG_CONFIGURATION_OPTIONS].
 * * **"debugCommandLineNonConfig":** Debug command line options that do not modify the result and should therefore not
 *   be considered in [getConfiguration]. Only used if the [log] level is set to [Level.DEBUG]. Defaults to
 *   [DEFAULT_DEBUG_NON_CONFIGURATION_OPTIONS].
 */
class ScanCode(
    name: String,
    config: ScannerConfiguration
) : LocalScanner(name, config) {
    class Factory : AbstractScannerFactory<ScanCode>("ScanCode") {
        override fun create(config: ScannerConfiguration) = ScanCode(scannerName, config)
    }

    companion object {
        private const val OUTPUT_FORMAT = "json-pp"
        private const val TIMEOUT = 300

        /**
         * Configuration options that are relevant for [getConfiguration] because they change the result file.
         */
        private val DEFAULT_CONFIGURATION_OPTIONS = listOf(
            "--copyright",
            "--license",
            "--ignore", "*$ORT_CONFIG_FILENAME",
            "--info",
            "--strip-root",
            "--timeout", TIMEOUT.toString()
        ) + NON_LICENSE_FILENAMES.flatMap { listOf("--ignore", it) }

        /**
         * Configuration options that are not relevant for [getConfiguration] because they do not change the result
         * file.
         */
        private val DEFAULT_NON_CONFIGURATION_OPTIONS = listOf(
            "--processes", max(1, Runtime.getRuntime().availableProcessors() - 1).toString()
        )

        /**
         * Debug configuration options that are relevant for [getConfiguration] because they change the result file.
         */
        private val DEFAULT_DEBUG_CONFIGURATION_OPTIONS = listOf("--license-diag")

        /**
         * Debug configuration options that are not relevant for [getConfiguration] because they do not change the
         * result file.
         */
        private val DEFAULT_DEBUG_NON_CONFIGURATION_OPTIONS = listOf("--verbose")

        private val OUTPUT_FORMAT_OPTION = if (OUTPUT_FORMAT.startsWith("json")) {
            "--$OUTPUT_FORMAT"
        } else {
            "--output-$OUTPUT_FORMAT"
        }

        // Note: The "(File: ...)" part in the patterns below is actually added by our own getRawResult() function.
        private val UNKNOWN_ERROR_REGEX = Pattern.compile(
            "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
                    "ERROR: Unknown error:\n.+\n(?<error>\\w+Error)(:|\n)(?<message>.*) \\(File: (?<file>.+)\\)",
            Pattern.DOTALL
        )

        private val UNKNOWN_LICENSE_KEYS = listOf(
            "free-unknown",
            "unknown",
            "unknown-license-reference"
        )

        private val TIMEOUT_ERROR_REGEX = Pattern.compile(
            "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
                    "ERROR: Processing interrupted: timeout after (?<timeout>\\d+) seconds. \\(File: (?<file>.+)\\)"
        )

        /**
         * Map messages about unknown issues to a more compact form. Return true if solely memory errors occurred,
         * return false otherwise.
         */
        internal fun mapUnknownIssues(issues: MutableList<OrtIssue>): Boolean {
            if (issues.isEmpty()) {
                return false
            }

            var onlyMemoryErrors = true

            val mappedIssues = issues.map { fullError ->
                UNKNOWN_ERROR_REGEX.matcher(fullError.message).let { matcher ->
                    if (matcher.matches()) {
                        val file = matcher.group("file")
                        val error = matcher.group("error")
                        if (error == "MemoryError") {
                            fullError.copy(message = "ERROR: MemoryError while scanning file '$file'.")
                        } else {
                            onlyMemoryErrors = false
                            val message = matcher.group("message").trim()
                            fullError.copy(message = "ERROR: $error while scanning file '$file' ($message).")
                        }
                    } else {
                        onlyMemoryErrors = false
                        fullError
                    }
                }
            }

            issues.clear()
            issues += mappedIssues.distinctBy { it.message }

            return onlyMemoryErrors
        }

        /**
         * Map messages about timeout errors to a more compact form. Return true if solely timeout errors occurred,
         * return false otherwise.
         */
        internal fun mapTimeoutErrors(issues: MutableList<OrtIssue>): Boolean {
            if (issues.isEmpty()) {
                return false
            }

            var onlyTimeoutErrors = true

            val mappedIssues = issues.map { fullError ->
                TIMEOUT_ERROR_REGEX.matcher(fullError.message).let { matcher ->
                    if (matcher.matches() && matcher.group("timeout") == TIMEOUT.toString()) {
                        val file = matcher.group("file")
                        fullError.copy(message = "ERROR: Timeout after $TIMEOUT seconds while scanning file '$file'.")
                    } else {
                        onlyTimeoutErrors = false
                        fullError
                    }
                }
            }

            issues.clear()
            issues += mappedIssues.distinctBy { it.message }

            return onlyTimeoutErrors
        }
    }

    override val scannerVersion = "3.2.0"
    override val resultFileExt = "json"

    private val scanCodeConfiguration = config.options?.get("ScanCode").orEmpty()

    private val configurationOptions = scanCodeConfiguration["commandLine"]?.split(" ")
        ?: DEFAULT_CONFIGURATION_OPTIONS
    private val nonConfigurationOptions = scanCodeConfiguration["commandLineNonConfig"]?.split(" ")
        ?: DEFAULT_NON_CONFIGURATION_OPTIONS
    private val debugConfigurationOptions = scanCodeConfiguration["debugCommandLine"]?.split(" ")
        ?: DEFAULT_DEBUG_CONFIGURATION_OPTIONS
    private val debugNonConfigurationOptions = scanCodeConfiguration["debugCommandLineNonConfig"]?.split(" ")
        ?: DEFAULT_DEBUG_NON_CONFIGURATION_OPTIONS

    val commandLineOptions by lazy {
        mutableListOf<String>().apply {
            addAll(configurationOptions)
            addAll(nonConfigurationOptions)

            if (log.delegate.isDebugEnabled) {
                addAll(debugConfigurationOptions)
                addAll(debugNonConfigurationOptions)
            }
        }.toList()
    }

    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "scancode.bat" else "scancode").joinToString(File.separator)

    override fun transformVersion(output: String): String {
        // "scancode --version" returns a string like "ScanCode version 2.0.1.post1.fb67a181" which might be preceded
        // by a line saying "Configuring ScanCode for first use...".
        val prefix = "ScanCode version "
        return output.lineSequence().first { it.startsWith(prefix) }.substring(prefix.length)
    }

    override fun bootstrap(): File {
        // Use the source code archive instead of the release artifact from S3 to enable OkHttp to cache the download
        // locally. For details see https://github.com/square/okhttp/issues/4355#issuecomment-435679393.
        val url = "https://github.com/oss-review-toolkit/ort/raw/upgrade-scancode-to-version-3.2/" +
                "scancode-toolkit-3.2.0.tar.bz2"

        log.info { "Downloading $scannerName from $url... " }

        val request = Request.Builder().get().url(url).build()

        return OkHttpClientHelper.execute(request).use { response ->
            val body = response.body

            if (response.code != HttpURLConnection.HTTP_OK || body == null) {
                throw IOException("Failed to download $scannerName from $url.")
            }

            if (response.cacheResponse != null) {
                log.info { "Retrieved $scannerName from local cache." }
            }

            val scannerArchive = createTempFile(ORT_NAME, "$scannerName-${url.substringAfterLast("/")}")
            scannerArchive.sink().buffer().use { it.writeAll(body.source()) }

            val unpackDir = createTempDir(ORT_NAME, "$scannerName-$scannerVersion").apply { deleteOnExit() }

            log.info { "file size: " + scannerArchive.length() }
            log.info { "Unpacking '$scannerArchive' to '$unpackDir'... " }
            scannerArchive.unpack(unpackDir)
            if (!scannerArchive.delete()) {
                log.warn { "Unable to delete temporary file '$scannerArchive'." }
            }

            val scannerDir = unpackDir.resolve("scancode-toolkit-$scannerVersion")

            scannerDir
        }
    }

    override fun getConfiguration() =
        configurationOptions.toMutableList().run {
            add(OUTPUT_FORMAT_OPTION)
            if (log.delegate.isDebugEnabled) {
                addAll(debugConfigurationOptions)
            }
            joinToString(" ")
        }

    override fun scanPathInternal(path: File, resultsFile: File): ScanResult {
        val startTime = Instant.now()

        val process = ProcessCapture(
            scannerPath.absolutePath,
            *commandLineOptions.toTypedArray(),
            path.absolutePath,
            OUTPUT_FORMAT_OPTION,
            resultsFile.absolutePath
        )

        val endTime = Instant.now()

        if (process.stderr.isNotBlank()) {
            log.debug { process.stderr }
        }

        val result = getRawResult(resultsFile)
        val summary = generateSummary(startTime, endTime, path, result)

        val issues = summary.issues.toMutableList()

        val hasOnlyMemoryErrors = mapUnknownIssues(issues)
        val hasOnlyTimeoutErrors = mapTimeoutErrors(issues)

        with(process) {
            if (isSuccess || hasOnlyMemoryErrors || hasOnlyTimeoutErrors) {
                return ScanResult(Provenance(), getDetails(), summary.copy(issues = issues), result)
            } else {
                throw ScanException(errorMessage)
            }
        }
    }

    override fun getRawResult(resultsFile: File) =
        if (resultsFile.isFile && resultsFile.length() > 0L) {
            jsonMapper.readTree(resultsFile)
        } else {
            EMPTY_JSON_NODE
        }

    private fun getFileCount(result: JsonNode): Int {
        // ScanCode 2.9.8 and above nest the files count in an extra header.
        result["headers"]?.forEach { header ->
            header["extra_data"]?.get("files_count")?.let {
                return it.intValue()
            }
        }

        // ScanCode 2.9.7 and below contain the files count at the top level.
        return result["files_count"]?.intValue() ?: 0
    }

    internal fun generateSummary(startTime: Instant, endTime: Instant, scanPath: File, result: JsonNode) =
        ScanSummary(
            startTime = startTime,
            endTime = endTime,
            fileCount = getFileCount(result),
            packageVerificationCode = calculatePackageVerificationCode(scanPath),
            licenseFindings = getLicenseFindings(result).toSortedSet(),
            copyrightFindings = getCopyrightFindings(result).toSortedSet(),
            issues = getIssues(result)
        )

    /**
     * Get the SPDX license id (or a fallback) for a license finding.
     */
    private fun getLicenseId(license: JsonNode): String {
        // The fact that ScanCode 3.0.2 uses an empty string here for licenses unknown to SPDX seems to have been a bug
        // in ScanCode, and it should have always been using null instead.
        var name = license["spdx_license_key"].textValueOrEmpty()

        if (name.isEmpty()) {
            val key = license["key"].textValue()
            name = if (key in UNKNOWN_LICENSE_KEYS) {
                SpdxConstants.NOASSERTION
            } else {
                // Starting with version 2.9.8, ScanCode uses "scancode" as a LicenseRef namespace, but only for SPDX
                // output formats, see https://github.com/nexB/scancode-toolkit/pull/1307.
                "LicenseRef-${scannerName.toLowerCase()}-$key"
            }
        }

        return name
    }

    private fun getIssues(result: JsonNode): List<OrtIssue> =
        result["files"]?.flatMap { file ->
            val path = file["path"].textValue()
            file["scan_errors"].map {
                OrtIssue(
                    source = scannerName,
                    message = "${it.textValue()} (File: $path)"
                )
            }
        }.orEmpty()

    /**
     * Get the license findings from the given [result].
     */
    private fun getLicenseFindings(result: JsonNode): List<LicenseFinding> {
        val licenseFindings = mutableListOf<LicenseFinding>()

        val files = result["files"]?.asSequence().orEmpty()
        files.flatMapTo(licenseFindings) { file ->
            val licenses = file["licenses"]?.asSequence().orEmpty()
            licenses.map {
                LicenseFinding(
                    license = getLicenseId(it),
                    location = TextLocation(
                        // The path is already relative as we run ScanCode with "--strip-root".
                        path = file["path"].textValue(),
                        startLine = it["start_line"].intValue(),
                        endLine = it["end_line"].intValue()
                    )
                )
            }
        }

        return licenseFindings
    }

    /**
     * Get the copyright findings from the given [result].
     */
    private fun getCopyrightFindings(result: JsonNode): List<CopyrightFinding> {
        val copyrightFindings = mutableListOf<CopyrightFinding>()

        val files = result["files"]?.asSequence().orEmpty()
        files.flatMapTo(copyrightFindings) { file ->
            val path = file["path"].textValue()

            val copyrights = file["copyrights"]?.asSequence().orEmpty()
            copyrights.flatMap { copyright ->
                val startLine = copyright["start_line"].intValue()
                val endLine = copyright["end_line"].intValue()

                // While ScanCode 2.9.2 was still using "statements", version 2.9.7 is using "value".
                val statements = (copyright["statements"]?.asSequence() ?: sequenceOf(copyright["value"]))

                statements.map { statement ->
                    CopyrightFinding(
                        statement = statement.textValue(),
                        location = TextLocation(
                            // The path is already relative as we run ScanCode with "--strip-root".
                            path = path,
                            startLine = startLine,
                            endLine = endLine
                        )
                    )
                }
            }
        }

        return copyrightFindings
    }
}
