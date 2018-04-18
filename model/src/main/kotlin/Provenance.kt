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

import com.fasterxml.jackson.annotation.JsonInclude

import java.time.Instant

/**
 * Provenance information about the scanned source code. Either [sourceArtifact] or [vcsInfo] can be set to a non-null
 * value. If both are null this indicates that no provenance information is available.
 */
data class Provenance(
        /**
         * The time when the source code was downloaded.
         */
        val downloadTime: Instant,

        /**
         * The source artifact that was downloaded, or null.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val sourceArtifact: RemoteArtifact? = null,

        /**
         * The VCS repository that was downloaded, or null.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val vcsInfo: VcsInfo? = null
) {
    init {
        require(sourceArtifact == null || vcsInfo == null) {
            "Provenance does not allow both 'sourceArtifact' and 'vcsInfo' to be set, otherwise it is ambiguous " +
                    "which was used."
        }
    }

    /**
     * True if [sourceArtifact] matches the [packages source artifact][Package.sourceArtifact], or if [vcsInfo] matches
     * either [the packages vcs][Package.vcs] or [the packages vcsProcessed][Package.vcsProcessed].
     */
    fun matches(pkg: Package) =
            sourceArtifact == pkg.sourceArtifact || vcsInfo == pkg.vcs || vcsInfo == pkg.vcsProcessed
}
