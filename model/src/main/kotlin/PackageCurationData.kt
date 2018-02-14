/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonProperty

import java.util.SortedSet

/**
 * This class contains curation data for a package. It is used to amend the automatically detected meta data for a
 * package with corrections. This is required because the meta data provided by a package can be wrong (e.g. outdated
 * VCS data) or incomplete.
 */
data class PackageCurationData(
        /**
         * The list of licenses the authors have declared for this package. This does not necessarily correspond to the
         * licenses as detected by a scanner. Both need to be taken into account for any conclusions.
         */
        @JsonProperty("declared_licenses")
        val declaredLicenses: SortedSet<String>? = null,

        /**
         * The description of the package, as provided by the package manager.
         */
        val description: String? = null,

        /**
         * The homepage of the package.
         */
        @JsonProperty("homepage_url")
        val homepageUrl: String? = null,

        /**
         * The remote artifact where the binary package can be downloaded.
         */
        @JsonProperty("binary_artifact")
        val binaryArtifact: RemoteArtifact? = null,

        /**
         * The remote artifact where the source package can be downloaded.
         */
        @JsonProperty("source_artifact")
        val sourceArtifact: RemoteArtifact? = null,

        /**
         * VCS-related information.
         */
        val vcs: VcsInfo? = null
) {
    /**
     * Apply the curation data to the provided package, by overriding all values of the original package with non-null
     * values of the curation data.
     *
     * @param pkg The package to curate.
     *
     * @return The curated package.
     */
    fun apply(pkg: Package): Package {
        val curatedVcs = if (vcs != null) {
            VcsInfo(
                    type = if (vcs.type.isNotBlank()) vcs.type else pkg.vcs.type,
                    url = if (vcs.url.isNotBlank()) vcs.url else pkg.vcs.url,
                    revision = if (vcs.revision.isNotBlank()) vcs.revision else pkg.vcs.revision,
                    path = if (vcs.path.isNotBlank()) vcs.path else pkg.vcs.path
            )
        } else {
            pkg.vcs
        }

        return Package(
                id = pkg.id,
                declaredLicenses = declaredLicenses ?: pkg.declaredLicenses,
                description = description ?: pkg.description,
                homepageUrl = homepageUrl ?: pkg.homepageUrl,
                binaryArtifact = binaryArtifact ?: pkg.binaryArtifact,
                sourceArtifact = sourceArtifact ?: pkg.sourceArtifact,
                vcs = curatedVcs
        )
    }
}
