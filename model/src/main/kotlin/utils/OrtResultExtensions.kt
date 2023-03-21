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

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver

/**
 * Add all resolutions from [resolutionProvider] that match the content of this [OrtResult] to
 * [OrtResult.resolvedConfiguration], overwriting any previously contained resolutions.
 */
fun OrtResult.addResolutions(resolutionProvider: ResolutionProvider): OrtResult {
    val resolutions = resolutionProvider.getResolutionsFor(this)
    return copy(resolvedConfiguration = resolvedConfiguration.copy(resolutions = resolutions))
}

/**
 * Create a [LicenseInfoResolver] for [this] [OrtResult]. If the resolver is used multiple times it should be stored
 * instead of calling this function multiple times for better performance.
 */
fun OrtResult.createLicenseInfoResolver(
    packageConfigurationProvider: PackageConfigurationProvider = PackageConfigurationProvider.EMPTY,
    copyrightGarbage: CopyrightGarbage = CopyrightGarbage(),
    addAuthorsToCopyrights: Boolean = false,
    archiver: FileArchiver? = null
) = LicenseInfoResolver(
        DefaultLicenseInfoProvider(this, packageConfigurationProvider),
        copyrightGarbage,
        addAuthorsToCopyrights,
        archiver,
        LicenseFilePatterns.getInstance()
    )

/**
 * Return the path where the repository given by [provenance] is linked into the source tree.
 */
fun OrtResult.getRepositoryPath(provenance: RepositoryProvenance): String {
    repository.nestedRepositories.forEach { (path, vcsInfo) ->
        if (vcsInfo.type == provenance.vcsInfo.type
            && vcsInfo.url == provenance.vcsInfo.url
            && vcsInfo.revision == provenance.resolvedRevision
        ) {
            return "/$path/"
        }
    }

    return "/"
}

/**
 * Copy this [OrtResult] and add all [labels] to the existing labels, overwriting existing labels on conflict.
 */
fun OrtResult.mergeLabels(labels: Map<String, String>) = copy(labels = this.labels + labels)
