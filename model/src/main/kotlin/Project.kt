/*
 * Copyright (c) 2017 HERE Europe B.V.
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
         * The name of the package manager that was used to discover this project, for example Maven or NPM.
         *
         * @see [Package.packageManager].
         */
        @JsonProperty("package_manager")
        val packageManager: String,

        /**
         * The namespace of the package, for example the group id in Maven or the scope in NPM.
         *
         * @see [Package.namespace].
         */
        val namespace: String,

        /**
         * The name of the project.
         */
        val name: String,

        /**
         * The version of the project.
         */
        val version: String,

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
         * The optional path inside the VCS to take into account. The actual meaning depends on the VCS provider. For
         * example for Git only this subfolder of the repository should be cloned, or for Git Repo it is interpreted as
         * the path to the manifest file.
         *
         * @see [Package.vcsPath].
         */
        @JsonProperty("vcs_path")
        val vcsPath: String?,

        /**
         * The name of the VCS provider, for example Git or SVN.
         *
         * @see [Package.vcsProvider].
         */
        @JsonProperty("vcs_provider")
        val vcsProvider: String,

        /**
         * The URL to the VCS repository.
         *
         * @see [Package.vcsUrl].
         */
        @JsonProperty("vcs_url")
        val vcsUrl: String,

        /**
         * The VCS-specific revision (tag, branch, SHA1) that this [version] of the project was built from.
         *
         * @see [Package.vcsRevision].
         */
        @JsonProperty("vcs_revision")
        val vcsRevision: String,

        /**
         * The URL to the project's homepage.
         */
        @JsonProperty("homepage_url")
        val homepageUrl: String,

        /**
         * The dependency scopes defined by this project.
         */
        val scopes: List<Scope>
) {

    /**
     * Return a [Package] representation of this [Project].
     */
    fun toPackage() = Package(
            packageManager = packageManager,
            namespace = namespace,
            name = name,
            version = version,
            declaredLicenses = declaredLicenses,
            description = "",
            homepageUrl = homepageUrl,
            downloadUrl = "",
            hash = "",
            hashAlgorithm = "",
            vcsPath = vcsPath,
            vcsProvider = vcsProvider,
            vcsUrl = vcsUrl,
            vcsRevision = vcsRevision
    )

}
