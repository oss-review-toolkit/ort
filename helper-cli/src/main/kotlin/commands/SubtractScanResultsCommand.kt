/*
 * Copyright (C) 2020-2021 HERE Europe B.V.
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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.readOrtResult
import org.ossreviewtoolkit.helper.common.writeOrtResult
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.expandTilde

internal class SubtractScanResultsCommand : CliktCommand(
    help = "Subtracts the given right-hand side scan results from the given left-hand side scan results. The output " +
            "is written to the given output ORT file."
) {
    private val lhsOrtFile by option(
        "--lhs-ort-file",
        help = "The ORT result containing the left-hand side scan result."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val rhsOrtFile by option(
        "--rhs-ort-file",
        help = "The ORT result containing the right-hand side scan result."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputOrtFile by option(
        "--output-ort-file",
        help = "The file to write the output ORT result to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val lhsOrtResult = readOrtResult(lhsOrtFile)
        val rhsOrtResult = readOrtResult(rhsOrtFile)

        val rhsScanSummaries = rhsOrtResult.scanner!!.results.scanResults.flatMap { it.value }.associateBy(
            keySelector = { it.provenance.key() },
            valueTransform = { it.summary }
        )

        val scanResults = lhsOrtResult.scanner!!.results.scanResults.mapValuesTo(sortedMapOf()) { (_, results) ->
            results.map { lhsScanResult ->
                val lhsSummary = lhsScanResult.summary
                val rhsSummary = rhsScanSummaries[lhsScanResult.provenance.key()]

                lhsScanResult.copy(summary = lhsSummary - rhsSummary)
            }
        }

        val result = lhsOrtResult.copy(
            scanner = lhsOrtResult.scanner!!.copy(
                results = lhsOrtResult.scanner!!.results.copy(
                    scanResults = scanResults
                )
            )
        )

        writeOrtResult(result, outputOrtFile)
    }
}

@Suppress("UnusedPrivateMember")
private operator fun ScanSummary.minus(other: ScanSummary?): ScanSummary {
    if (other == null) return this

    return copy(
        licenseFindings = (licenseFindings - other.licenseFindings).toSortedSet(),
        copyrightFindings = (copyrightFindings - other.copyrightFindings).toSortedSet()
    )
}

private data class Key(
    val vcsType: VcsType? = null,
    val vcsUrl: String? = null,
    val vcsRevision: String? = null,
    val vcsPath: String? = null,
    val sourceArtifactUrl: String? = null
)

private fun Provenance.key(): Key =
    when (this) {
        is ArtifactProvenance -> Key(sourceArtifactUrl = sourceArtifact.url)

        is RepositoryProvenance -> {
            Key(
                vcsType = vcsInfo.type,
                vcsUrl = vcsInfo.url,
                vcsRevision = vcsInfo.revision,
                vcsPath = vcsInfo.path
            )
        }

        else -> Key()
    }
