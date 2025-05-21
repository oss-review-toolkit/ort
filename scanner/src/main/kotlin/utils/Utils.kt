/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.utils

import java.io.File

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.utils.getKnownProvenancesWithoutVcsPath
import org.ossreviewtoolkit.model.utils.vcsPath
import org.ossreviewtoolkit.scanner.ScanStorageException

/**
 * Throw a [ScanStorageException] if [provenance] is a [RepositoryProvenance] with a non-empty VCS path.
 */
internal fun requireEmptyVcsPath(provenance: Provenance) {
    if (provenance is RepositoryProvenance && provenance.vcsInfo.path.isNotEmpty()) {
        throw ScanStorageException("Repository provenances with a non-empty VCS path are not supported.")
    }
}

/**
 * Return a map of VCS paths for each [KnownProvenance] contained in [provenances].
 */
fun getVcsPathsForProvenances(provenances: Set<ProvenanceResolutionResult>) =
    buildMap<KnownProvenance, MutableSet<String>> {
        provenances.forEach { provenance ->
            val packageVcsPath = provenance.packageProvenance.vcsPath

            provenance.getKnownProvenancesWithoutVcsPath().forEach { (repositoryPath, provenance) ->
                getVcsPathForRepositoryOrNull(packageVcsPath, repositoryPath)?.let { vcsPath ->
                    getOrPut(provenance) { mutableSetOf() } += vcsPath
                }
            }
        }
    }

/**
 * Filter [ScanResult]s by the VCS paths of the [KnownProvenance]s contained in [vcsPathsForProvenances].
 */
fun filterScanResultsByVcsPaths(
    allScanResults: List<ScanResult>,
    vcsPathsForProvenances: Map<KnownProvenance, MutableSet<String>>
) = allScanResults.map { scanResult ->
    scanResult.copy(provenance = scanResult.provenance.alignRevisions())
}.mapNotNullTo(mutableSetOf()) { scanResult ->
    vcsPathsForProvenances[scanResult.provenance]?.let {
        scanResult.copy(summary = scanResult.summary.filterByPaths(it))
    }
}

/**
 * Return the VCS path applicable to a (sub-) repository which appears under [repositoryPath] in the source tree of
 * a package residing in [vcsPath], or null if the subtrees for [repositoryPath] and [vcsPath] are disjoint.
 */
private fun getVcsPathForRepositoryOrNull(vcsPath: String, repositoryPath: String): String? {
    val repoPathFile = File(repositoryPath)
    val vcsPathFile = File(vcsPath)

    return if (repoPathFile.startsWith(vcsPathFile)) {
        ""
    } else {
        runCatching { vcsPathFile.relativeTo(repoPathFile).invariantSeparatorsPath }.getOrNull()
    }
}

internal fun <T : Provenance> T.alignRevisions(): Provenance =
    if (this is RepositoryProvenance) {
        copy(vcsInfo = vcsInfo.copy(revision = resolvedRevision))
    } else {
        this
    }
