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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.here.ort.util.normalizePackageName

import com.here.ort.util.normalizePackageName
import com.here.ort.util.normalizeVcsUrl

import com.vdurmont.semver4j.Semver

import java.util.SortedSet

/**
 * A generic descriptor for a software package. It contains all relevant meta-data about a package like the name,
 * version, and how to retrieve the package and its source code. It does not contain information about the package's
 * dependencies, however. This is because at this stage we would only be able to get the declared dependencies, whereas
 * we are interested in the resolved dependencies. Resolved dependencies might differ from declared dependencies due to
 * specified version ranges, or change depending on how the package is used in a project due to the build system's
 * dependency resolution process. For example, if multiple versions of the same package are used in a project, the build
 * system might decide to align on a single version of that package.
 */
@JsonIgnoreProperties("identifier", "normalizedVcsUrl", "semverType")
data class Package(
        /**
         * The name of the package manager that was used to discover this package, for example Maven or NPM.
         */
        @JsonProperty("package_manager")
        val packageManager: String,

        /**
         * The namespace of the package, for example the group id in Maven or the scope in NPM.
         */
        val namespace: String,

        /**
         * The name of the package.
         */
        val name: String,

        /**
         * The version of the package.
         */
        val version: String,

        /**
         * The list of licenses the authors have declared for this package. This does not necessarily correspond to the
         * licenses as detected by a scanner. Both need to be taken into account for any conclusions.
         */
        @JsonProperty("declared_licenses")
        val declaredLicenses: SortedSet<String>,

        /**
         * The description of the package, as provided by the package manager.
         */
        val description: String,

        /**
         * The homepage of the package.
         */
        @JsonProperty("homepage_url")
        val homepageUrl: String?,

        /**
         * The optional URL to the binary artifact of the package.
         */
        @JsonProperty("download_url")
        val downloadUrl: String?,

        /**
         * The optional hash value of the binary artifact of the package.
         */
        val hash: String,

        /**
         * The optional name of the algorithm used to calculate the hash.
         */
        @JsonProperty("hash_algorithm")
        val hashAlgorithm: String?,

        /**
         * The optional name of the VCS provider, for example Git or SVN.
         */
        @JsonProperty("vcs_provider")
        val vcsProvider: String?,

        /**
         * The optional URL to the VCS repository.
         */
        @JsonProperty("vcs_url")
        val vcsUrl: String?,

        /**
         * The optional VCS-specific revision (tag, branch, SHA1) that this [version] of the package was built from.
         */
        @JsonProperty("vcs_revision")
        val vcsRevision: String?,

        /**
         * The optional path inside the VCS to take into account. The actual meaning depends on the VCS provider. For
         * example for Git only this subfolder of the repository should be cloned, or for Git Repo it is interpreted as
         * the path to the manifest file.
         */
        @JsonProperty("vcs_path")
        val vcsPath: String?
) : Comparable<Package> {
    /**
     * The unique identifier for this package, created from [packageManager], [namespace], [name], and [version].
     */
    val identifier = "$packageManager:$namespace:$name:$version"

    /**
     * The [Semver.SemverType] used for the [version] of this package.
     */
    val semverType = when (packageManager) {
        "NPM" -> Semver.SemverType.NPM
        else -> Semver.SemverType.STRICT
    }

    /**
     * The normalized VCS URL.
     *
     * @see normalizeVcsUrl
     */
    val normalizedVcsUrl = vcsUrl?.let { normalizeVcsUrl(it, semverType) }

    /**
     * The normalized package name, so it can be used as downloaded package directory.
     *
     * @see normalizePackageName
     */
    val normalizedName = name?.let { normalizePackageName(it) }

    /**
     * Return a template [PackageReference] to refer to this [Package]. It is only a template because e.g. the
     * dependencies still need to be filled out.
     */
    fun toReference(dependencies: SortedSet<PackageReference> = sortedSetOf())
            = PackageReference(namespace, name, version, dependencies)

    /**
     * A comparison function to sort packages by their identifier.
     */
    override fun compareTo(other: Package) = compareValuesBy(this, other, { it.identifier })
}
