/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude

import java.time.Instant

/**
 * Provenance information about the scanned source code. Either [sourceArtifact] or [vcsInfo] can be set to a non-null
 * value. If both are null this indicates that no provenance information is available.
 */
data class Provenance(
        /**
         * The time when the source code was downloaded, or [Instant.EPOCH] if unknown (e.g. for source code that was
         * downloaded separately from running ORT).
         */
        @JsonAlias("downloadTime")
        val downloadTime: Instant = Instant.EPOCH,

        /**
         * The source artifact that was downloaded, or null.
         */
        @JsonAlias("sourceArtifact")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val sourceArtifact: RemoteArtifact? = null,

        /**
         * The VCS repository that was downloaded, or null.
         */
        @JsonAlias("vcsInfo")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val vcsInfo: VcsInfo? = null,

        /**
         * The original [VcsInfo] that was used to download the source code. It can be different to [vcsInfo] if any
         * automatic detection took place. For example if the original [VcsInfo] does not contain any revision and the
         * revision was automatically detected by searching for a tag that matches the version of the package there
         * would be no way to match the package to the [Provenance] without downloading the source code and searching
         * for the tag again.
         */
        @JsonAlias("originalVcsInfo")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val originalVcsInfo: VcsInfo? = null,

        /**
         * A map that holds arbitrary data. Can be used by third-party tools to add custom data to the model.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val data: CustomData = emptyMap()
) {
    init {
        require(sourceArtifact == null || vcsInfo == null) {
            "Provenance does not allow both 'sourceArtifact' and 'vcsInfo' to be set, otherwise it is ambiguous " +
                    "which was used."
        }
    }

    /**
     * True if this [Provenance] refers to the same source code as [pkg], assuming that it belongs to the package id.
     */
    fun matches(pkg: Package): Boolean {
        // TODO: Only comparing the hashes of the source artifacts might be sufficient.
        if (sourceArtifact != null) {
            // Note that pkg.sourceArtifact is non-nullable.
            return sourceArtifact.copy(data = emptyMap()) == pkg.sourceArtifact.copy(data = emptyMap())
        }

        // If the VCS information does not have a resolved revision it means that there was an issue with downloading
        // the source code.
        if (vcsInfo?.resolvedRevision == null) {
            return false
        }

        // If pkg.vcsProcessed equals originalVcsInfo or vcsInfo this provenance was definitely created when downloading
        // this package.
        if (pkg.vcsProcessed == originalVcsInfo || pkg.vcsProcessed == vcsInfo) {
            return true
        }

        return listOf(pkg.vcs, pkg.vcsProcessed).any {
            // If "it" has a resolved revision it must be equal to the resolved revision of vcsInfo, otherwise the
            // revision of "it" has to equal either the revision or the resolved revision of vcsInfo.
            it.type.equals(vcsInfo.type, true) && it.url == vcsInfo.url && it.path == vcsInfo.path &&
                    it.resolvedRevision?.let {
                vcsInfo.resolvedRevision == it
            } ?: vcsInfo.resolvedRevision == it.revision || vcsInfo.revision == it.revision
        }
    }
}
