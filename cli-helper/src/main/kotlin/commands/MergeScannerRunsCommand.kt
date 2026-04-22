/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.helper.utils.writeOrtResult
import org.ossreviewtoolkit.model.FileList
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.zipWithSets

internal class MergeScannerRunsCommand : OrtHelperCommand(
    help = "Merge the scanner run from the given other ORT file into the given ORT file and write the result to the " +
        "output ORT file. For unmergeable data, such as scanner configuration, the data from the give ORT file " +
        "is used. All data outside of the scanner run is taken only from the given ORT file. Any contained evaluator " +
        "run is discarded, to prevent likely inconsistencies."
) {
    private val ortFile by option(
        "--ort-file",
        help = "The input ORT file into which the scanner run is to be merged."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val otherOrtFile by option(
        "--other-ort-file",
        help = "The other input ORT file from which to take the scanner run from."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputOrtFile by option(
        "--output-ort-file", "-o",
        help = "The target ORT file to write the merge result to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val result = mergeScannerRuns(
            result = readOrtResult(ortFile),
            otherResult = readOrtResult(otherOrtFile)
        )

        writeOrtResult(result, outputOrtFile)
    }
}

private fun mergeScannerRuns(result: OrtResult, otherResult: OrtResult): OrtResult {
    require(result.analyzer == otherResult.analyzer) {
        "The analyzer runs in both input files must be identical, but differ."
    }

    return result.copy(
        scanner = merge(
            run = requireNotNull(result.scanner) {
                "The ORT file must contain a scanner run, but it does not."
            },
            otherRun = requireNotNull(otherResult.scanner) {
                "The other ORT file must contain a scanner run, but it does not."
            }
        ),
        evaluator = null // Discard any evaluator run, to avoid possible inconsistencies:
    )
}

@JvmName("mergeScannerRun")
private fun merge(run: ScannerRun, otherRun: ScannerRun): ScannerRun =
    run.copy(
        provenances = merge(run.provenances, otherRun.provenances),
        scanResults = merge(run.scanResults, otherRun.scanResults),
        issues = run.issues.zipWithSets(otherRun.issues),
        scanners = run.scanners.zipWithSets(otherRun.scanners),
        files = merge(run.files, otherRun.files)
    )

@JvmName("mergeProvenanceResolutionResult")
private fun merge(
    result: Set<ProvenanceResolutionResult>,
    otherResult: Set<ProvenanceResolutionResult>
): Set<ProvenanceResolutionResult> {
    val distinctResultsForId = (result + otherResult).groupBy { it.id }.mapValues { it.value.distinct() }
    val idsForConflicts = distinctResultsForId.filter { it.value.size > 1 }.keys

    require(idsForConflicts.isEmpty()) {
        "The provenance resolution contains conflicting results for the following ids: " +
            "${idsForConflicts.joinToString { it.toCoordinates() }}."
    }

    return distinctResultsForId.values.mapTo(mutableSetOf()) { it.single() }
}

@JvmName("mergeScanResult")
private fun merge(result: Set<ScanResult>, otherResult: Set<ScanResult>): Set<ScanResult> {
    // TODO: It might be necessary to make this more strict to allow only one scan results per unique
    //       (provenance, scanner.name). Tolerate it for now, as no concrete issue is foreseen, to not introduce a
    //       limitation without need.
    return result + otherResult
}

@JvmName("mergeFileLists")
private fun merge(list: Set<FileList>, otherList: Set<FileList>): Set<FileList> {
    val distinctFileListForProvenance = (list + otherList).groupBy { it.provenance }.mapValues { it.value.distinct() }
    val provenancesForConflicts = distinctFileListForProvenance.filter { it.value.size > 1 }.keys

    require(provenancesForConflicts.isEmpty()) {
        "The file lists contain conflicting entries for the following provenances: \n" +
            "${provenancesForConflicts.toYaml()}\n."
    }

    return distinctFileListForProvenance.values.mapTo(mutableSetOf()) { it.single() }
}
