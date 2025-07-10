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

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.FileList
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.utils.alignRevisions
import org.ossreviewtoolkit.model.utils.clearVcsPath
import org.ossreviewtoolkit.model.utils.toPurl
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
        results = results.toMap()
    )

/**
 * Return the absolute file for the functional test assets at the given [path].
 */
fun getAssetFile(path: String): File = File("src/funTest/assets", path).absoluteFile

/**
 * Return a string representation of the [expectedResult] contents that has placeholders replaced. If a [definitionFile]
 * is provided, values that can be derived from it, like the VCS revision, are also replaced. Additionally, [custom]
 * regex replacements with substitutions can be specified.
 */
fun patchExpectedResult(
    expectedResult: String,
    definitionFile: File? = null,
    custom: Map<String, String> = emptyMap()
): String {
    val env = Environment()

    val replacements = buildMap {
        put("<REPLACE_JDK>", env.buildJdk)
        put("<REPLACE_JAVA>", env.javaVersion)
        put("<REPLACE_OS>", env.os)
        put("\"<REPLACE_PROCESSORS>\"", env.processors.toString())
        put("\"<REPLACE_MAX_MEMORY>\"", env.maxMemory.toString())

        if (definitionFile != null) {
            val projectDir = definitionFile.parentFile
            val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
            val url = vcsDir.getRemoteUrl()
            val path = vcsDir.getPathToRoot(projectDir)

            put("<REPLACE_DEFINITION_FILE_PATH>", "$path/${definitionFile.name}")
            put("<REPLACE_ABSOLUTE_DEFINITION_FILE_PATH>", definitionFile.absoluteFile.invariantSeparatorsPath)
            put("<REPLACE_URL>", url)
            put("<REPLACE_REVISION>", vcsDir.getRevision())
            put("<REPLACE_PATH>", path)
            put("<REPLACE_URL_PROCESSED>", normalizeVcsUrl(url))
        }

        putAll(custom)
    }

    return replacements.entries.fold(expectedResult) { text, (pattern, replacement) ->
        text.replace(pattern.toRegex(), replacement)
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
        .replace(JAVA_VERSION_REGEX) { "${it.groupValues[1]}: \"${Environment.JAVA_VERSION}\"" }
        .replace(ENV_VAR_REGEX) { "${it.groupValues[1]} {}" }
        .replace(ENV_TOOL_REGEX) { "${it.groupValues[1]} {}" }
        .replace(TIMESTAMP_REGEX) { "${it.groupValues[1]}: \"${Instant.EPOCH}\"" }
        .replaceIf(patchStartAndEndTime, START_AND_END_TIME_REGEX) { "${it.groupValues[1]}: \"${Instant.EPOCH}\"" }
}

/**
 * Create a [ScannerRun] with the given [pkgScanResults].
 */
fun scannerRunOf(vararg pkgScanResults: Pair<Identifier, List<ScanResult>>): ScannerRun {
    val pkgScanResultsWithKnownProvenance = pkgScanResults.associate { (id, scanResultsForId) ->
        id to scanResultsForId.map { scanResult ->
            scanResult.takeIf { scanResult.provenance is KnownProvenance } ?: scanResult.copy(
                provenance = ArtifactProvenance(
                    sourceArtifact = RemoteArtifact(
                        url = id.toPurl(),
                        hash = Hash.NONE
                    )
                )
            )
        }
    }

    val scanResults = pkgScanResultsWithKnownProvenance.values.flatten()
        .map { scanResult ->
            scanResult.copy(
                provenance = (scanResult.provenance as? RepositoryProvenance)?.clearVcsPath()?.alignRevisions()
                    ?: scanResult.provenance
            )
        }
        .groupBy { it.provenance to it.scanner }
        .values
        .mapTo(mutableSetOf()) { scanResults ->
            scanResults.reduce { acc, next -> acc + next }
        }

    val filePathsByProvenance = scanResults.mapNotNull { scanResult ->
        val provenance = scanResult.provenance as? KnownProvenance ?: return@mapNotNull null

        val paths = buildSet {
            scanResult.summary.copyrightFindings.mapTo(this) { it.location.path }
            scanResult.summary.licenseFindings.mapTo(this) { it.location.path }
            scanResult.summary.snippetFindings.mapTo(this) { it.sourceLocation.path }
        }

        provenance to paths
    }.groupBy({ it.first }, { it.second }).mapValues { it.value.flatten().toSet() }

    val files = filePathsByProvenance.mapTo(mutableSetOf()) { (provenance, paths) ->
        FileList(
            provenance = provenance,
            files = paths.mapTo(mutableSetOf()) {
                FileList.Entry(path = it, sha1 = HashAlgorithm.SHA1.calculate(it.encodeToByteArray()))
            }
        )
    }

    val scanners = pkgScanResults.associate { (id, scanResultsForId) ->
        id to scanResultsForId.mapTo(mutableSetOf()) { it.scanner.name }
    }

    return ScannerRun.EMPTY.copy(
        provenances = pkgScanResultsWithKnownProvenance.mapTo(mutableSetOf()) { (id, scanResultsForId) ->
            ProvenanceResolutionResult(
                id = id,
                packageProvenance = scanResultsForId.firstOrNull()?.provenance as KnownProvenance
            )
        },
        scanResults = scanResults,
        files = files,
        scanners = scanners
    )
}

fun identifierToPackage(id: String): Package = Identifier(id).let { Package.EMPTY.copy(id = it, purl = it.toPurl()) }
