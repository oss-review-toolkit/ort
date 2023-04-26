/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.test

import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.AdvisorRecord
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.NestedProvenanceResolutionResult
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

val USER_DIR = File(System.getProperty("user.dir"))

private val ORT_VERSION_REGEX = Regex("(ort_version): \".*\"")
private val JAVA_VERSION_REGEX = Regex("(java_version): \".*\"")
private val ENV_VAR_REGEX = Regex("(\\s{4}variables:)\\n(?:\\s{6}.+)+")
private val ENV_TOOL_REGEX = Regex("(\\s{4}tool_versions:)\\n(?:\\s{6}.+)+")
private val START_AND_END_TIME_REGEX = Regex("((start|end)_time): \".*\"")
private val TIMESTAMP_REGEX = Regex("(timestamp): \".*\"")

/**
 * Create an [AdvisorRun] with the given [results].
 */
fun advisorRunOf(vararg results: Pair<Identifier, List<AdvisorResult>>): AdvisorRun =
    AdvisorRun(
        startTime = Instant.now(),
        endTime = Instant.now(),
        environment = Environment(),
        config = AdvisorConfiguration(),
        results = AdvisorRecord(results.toMap())
    )

/**
 * Return the content of the fun test asset file located under [path] relative to the 'assets' directory as text.
 */
fun getAssetAsString(path: String): String = getAssetFile(path).readText()

/**
 * Return the absolute file for the functional test assets at the given [path].
 */
fun getAssetFile(path: String): File = File("src/funTest/assets", path).absoluteFile

/**
 * Return a string representation of the [expectedResultFile] contents that has placeholders replaced. If a
 * [definitionFile] is provided, values that can be derived from it, like the VCS revision, are also replaced.
 * Additionally, [custom] placeholders can be replaced as well.
 */
fun patchExpectedResult(
    expectedResultFile: File,
    definitionFile: File? = null,
    custom: Map<String, String> = emptyMap()
): String {
    val replacements = buildMap {
        put("<REPLACE_JAVA>", System.getProperty("java.version"))
        put("<REPLACE_OS>", System.getProperty("os.name"))
        put("\"<REPLACE_PROCESSORS>\"", Runtime.getRuntime().availableProcessors().toString())
        put("\"<REPLACE_MAX_MEMORY>\"", Runtime.getRuntime().maxMemory().toString())

        if (definitionFile != null) {
            val projectDir = definitionFile.parentFile
            val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
            val url = vcsDir.getRemoteUrl()
            val path = vcsDir.getPathToRoot(projectDir)

            put("<REPLACE_DEFINITION_FILE_PATH>", "$path/${definitionFile.name}")
            put("<REPLACE_ABSOLUTE_DEFINITION_FILE_PATH>", definitionFile.absolutePath)
            put("<REPLACE_URL>", url)
            put("<REPLACE_REVISION>", vcsDir.getRevision())
            put("<REPLACE_PATH>", path)
            put("<REPLACE_URL_PROCESSED>", normalizeVcsUrl(url))
        }

        putAll(custom)
    }

    return replacements.entries.fold(expectedResultFile.readText()) { text, (oldValue, newValue) ->
        text.replace(oldValue, newValue)
    }
}

/**
 * Return a patched version of the [result] string, which is assumed to represent an ORT result (but is not required to
 * do). Values that usually change between ORT runs, like timestamps, are replaced with invariant values to allow for
 * easy comparison with expected results. If [patchStartAndEndTime] is true, start and end times are also replaced.
 * Additionally, [custom] regex replacements with substitutions can be specified.
 */
fun patchActualResult(
    result: String,
    custom: Map<String, String> = emptyMap(),
    patchStartAndEndTime: Boolean = false
): String {
    fun String.replaceIf(condition: Boolean, regex: Regex, transform: (MatchResult) -> CharSequence) =
        if (condition) replace(regex, transform) else this

    return custom.entries.fold(result) { text, (pattern, replacement) -> text.replace(pattern.toRegex(), replacement) }
        .replace(ORT_VERSION_REGEX) { "${it.groupValues[1]}: \"HEAD\"" }
        .replace(JAVA_VERSION_REGEX) { "${it.groupValues[1]}: \"${System.getProperty("java.version")}\"" }
        .replace(ENV_VAR_REGEX) { "${it.groupValues[1]} {}" }
        .replace(ENV_TOOL_REGEX) { "${it.groupValues[1]} {}" }
        .replace(TIMESTAMP_REGEX) { "${it.groupValues[1]}: \"${Instant.EPOCH}\"" }
        .replaceIf(patchStartAndEndTime, START_AND_END_TIME_REGEX) { "${it.groupValues[1]}: \"${Instant.EPOCH}\"" }
}

fun readOrtResult(file: String) = readOrtResult(File(file))

fun readOrtResult(file: File) = file.mapper().readValue<OrtResult>(patchExpectedResult(file))

fun scannerRunOf(vararg scanResults: Pair<Identifier, List<ScanResult>>): ScannerRun =
    ScannerRun.EMPTY.copy(
        provenances = scanResults.map { (id, scanResultsForId) ->
            val provenance = scanResultsForId.firstOrNull()?.provenance ?: UnknownProvenance
            val issue = if (provenance is UnknownProvenance) {
                Issue(source = "Scanner", message = "Could not resolve provenance.")
            } else {
                null
            }

            ProvenanceResolutionResult(id, provenance, issue)
        },
        nestedProvenances = scanResults.mapNotNull { (_, scanResultsForId) ->
            val provenance = scanResultsForId.firstOrNull()?.provenance ?: UnknownProvenance

            if (provenance is RepositoryProvenance) {
                NestedProvenanceResolutionResult(provenance, emptyMap(), null)
            } else {
                null
            }
        },
        scanResults = scanResults.flatMap { it.second }
    )
