/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.utils

import java.net.URI

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.utils.common.getDuplicates

fun String.prependPath(prefix: String): String = if (prefix.isBlank()) this else "${prefix.removeSuffix("/")}/$this"

fun TextLocation.prependedPath(prefix: String): String = path.prependPath(prefix)

fun TextLocation.prependPath(prefix: String): TextLocation =
    if (prefix.isEmpty()) this else copy(path = path.prependPath(prefix))

/**
 * Return the VCS path if this is a [RepositoryProvenance] or else an empty string.
 */
val Provenance?.vcsPath: String
    get() = (this as? RepositoryProvenance)?.vcsInfo?.path.orEmpty()

/**
 * Return all provenances contained in this [ProvenanceResolutionResult], with each having empty VCS path and the
 * revision of the VcsInfo equal to the resolved revision in case of a repository provenance.
 */
fun ProvenanceResolutionResult.getKnownProvenancesWithoutVcsPath(): Map<String, KnownProvenance> =
    buildMap {
        when (packageProvenance) {
            is RepositoryProvenance -> put("", packageProvenance.clearVcsPath().alignRevisions())
            is ArtifactProvenance -> put("", packageProvenance)
            else -> {}
        }

        subRepositories.mapValuesTo(this) { (_, vcsInfo) ->
            RepositoryProvenance(vcsInfo = vcsInfo, resolvedRevision = vcsInfo.revision)
        }
    }

/**
 * Return a copy of this [RepositoryProvenance] with an empty VCS path.
 */
fun RepositoryProvenance.clearVcsPath() = copy(vcsInfo = vcsInfo.copy(path = ""))

/**
 * Return a copy of this [RepositoryProvenance] with [VcsInfo] revision set to the resolved revision of this
 * [RepositoryProvenance].
 */
fun RepositoryProvenance.alignRevisions(): RepositoryProvenance =
    copy(vcsInfo = vcsInfo.copy(revision = resolvedRevision))

/**
 * Return the repo manifest path parsed from this string. The string is interpreted as a URL and the manifest path is
 * expected as the value of the "manifest" query parameter, for example:
 * http://example.com/repo.git?manifest=manifest.xml.
 *
 * Return an empty string if no "manifest" query parameter is found or this string cannot be parsed as a URL.
 */
fun String.parseRepoManifestPath() =
    runCatching {
        URI(this).query.splitToSequence("&")
            .map { it.split('=', limit = 2) }
            .find { it.first() == "manifest" }
            ?.get(1)
            ?.takeUnless { it.isEmpty() }
    }.getOrNull()

internal fun List<SourceCodeOrigin>.requireNotEmptyNoDuplicates() {
    require(isNotEmpty()) {
        "'sourceCodeOrigins' must not be empty."
    }

    val duplicates = getDuplicates()
    require(duplicates.isEmpty()) {
        "'sourceCodeOrigins' must not contain duplicates. Duplicates: $duplicates"
    }
}
