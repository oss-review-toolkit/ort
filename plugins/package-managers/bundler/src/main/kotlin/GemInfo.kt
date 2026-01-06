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

package org.ossreviewtoolkit.plugins.packagemanagers.bundler

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.orEmpty

internal data class GemInfo(
    val name: String,
    val version: String,
    val homepageUrl: String,
    val authors: Set<String>,
    val declaredLicenses: Set<String>,
    val description: String,
    val runtimeDependencies: Set<String>,
    val vcs: VcsInfo,
    val artifact: RemoteArtifact
) {
    companion object {
        fun createFromMetadata(spec: GemSpec): GemInfo {
            val runtimeDependencies = spec.dependencies.mapNotNullTo(mutableSetOf()) { (name, type) ->
                name.takeIf { type == VersionDetails.Scope.RUNTIME.toString() }
            }

            val homepage = spec.homepage.orEmpty()
            val info = spec.description ?: spec.summary

            return GemInfo(
                spec.name,
                spec.version.version,
                homepage,
                spec.authors.mapToSetOfNotEmptyStrings(),
                spec.licenses.mapToSetOfNotEmptyStrings(),
                info.orEmpty(),
                runtimeDependencies,
                VcsHost.parseUrl(homepage),
                RemoteArtifact.EMPTY
            )
        }

        fun createFromGem(details: VersionDetails): GemInfo {
            val runtimeDependencies = details.dependencies[VersionDetails.Scope.RUNTIME]
                ?.mapTo(mutableSetOf()) { it.name }
                .orEmpty()

            val vcs = listOfNotNull(details.sourceCodeUri, details.homepageUri)
                .mapToSetOfNotEmptyStrings()
                .firstOrNull()
                ?.let { VcsHost.parseUrl(it) }
                .orEmpty()

            val artifact = if (details.gemUri != null && details.sha != null) {
                RemoteArtifact(details.gemUri, Hash.create(details.sha))
            } else {
                RemoteArtifact.EMPTY
            }

            return GemInfo(
                details.name,
                details.version,
                details.homepageUri.orEmpty(),
                details.authors?.split(',').mapToSetOfNotEmptyStrings(),
                details.licenses.mapToSetOfNotEmptyStrings(),
                details.info.orEmpty(),
                runtimeDependencies,
                vcs,
                artifact
            )
        }

        private fun Collection<String>?.mapToSetOfNotEmptyStrings(): Set<String> =
            this?.mapNotNullTo(mutableSetOf()) { string -> string.trim().ifEmpty { null } }.orEmpty()
    }

    fun merge(other: GemInfo): GemInfo {
        require(name == other.name && version == other.version) {
            "Cannot merge specs for different gems."
        }

        return GemInfo(
            name,
            version,
            homepageUrl.ifEmpty { other.homepageUrl },
            authors.ifEmpty { other.authors },
            declaredLicenses.ifEmpty { other.declaredLicenses },
            description.ifEmpty { other.description },
            runtimeDependencies.ifEmpty { other.runtimeDependencies },
            vcs.takeUnless { it == VcsInfo.EMPTY } ?: other.vcs,
            artifact.takeUnless { it == RemoteArtifact.EMPTY } ?: other.artifact
        )
    }
}
