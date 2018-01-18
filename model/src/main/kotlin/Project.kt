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
 * A class describing a software project. A [Project] is very similar to a [Package] but contains some additional
 * meta-data like e.g. the [homepageUrl]. Most importantly, it defines the dependency scopes that refer to the actual
 * packages.
 */
data class Project(
        /**
         * The unique identifier of this project.
         */
        val id: Identifier,

        /**
         * The list of licenses the authors have declared for this package. This does not necessarily correspond to the
         * licenses as detected by a scanner. Both need to be taken into account for any conclusions.
         */
        @JsonProperty("declared_licenses")
        val declaredLicenses: SortedSet<String>,

        /**
         * Alternate project names, like abbreviations or code names.
         */
        val aliases: List<String>,

        /**
         * Original VCS-related information as defined in the [Project]'s meta-data.
         */
        val vcs: VcsInfo,

        /**
         * Processed VCS-related information about the [Project] that has e.g. common mistakes corrected.
         */
        @JsonProperty("vcs_processed")
        val vcsProcessed: VcsInfo = vcs.normalize(),

        /**
         * The URL to the project's homepage.
         */
        @JsonProperty("homepage_url")
        val homepageUrl: String,

        /**
         * The dependency scopes defined by this project.
         */
        val scopes: SortedSet<Scope>
) {
    /**
     * Return a [Package] representation of this [Project].
     */
    fun toPackage() = Package(
            id = id,
            declaredLicenses = declaredLicenses,
            description = "",
            homepageUrl = homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = vcs,
            vcsProcessed = vcsProcessed
    )

    companion object {
        /**
         * A constant for a [Project] where all properties are empty strings or empty collections.
         */
        @JvmField
        val EMPTY = Project(
                id = Identifier.EMPTY,
                declaredLicenses = sortedSetOf(),
                aliases = emptyList(),
                vcs = VcsInfo.EMPTY,
                homepageUrl = "",
                scopes = sortedSetOf()
        )
    }
}
