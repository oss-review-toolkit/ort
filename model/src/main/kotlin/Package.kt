/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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
data class Package(
        /**
         * The unique identifier of this package.
         */
        val id: Identifier,

        /**
         * The list of licenses the authors have declared for this package. This does not necessarily correspond to the
         * licenses as detected by a scanner. Both need to be taken into account for any conclusions.
         */
        val declaredLicenses: SortedSet<String>,

        /**
         * The description of the package, as provided by the package manager.
         */
        val description: String,

        /**
         * The homepage of the package.
         */
        val homepageUrl: String,

        /**
         * The remote artifact where the binary package can be downloaded.
         */
        val binaryArtifact: RemoteArtifact,

        /**
         * The remote artifact where the source package can be downloaded.
         */
        val sourceArtifact: RemoteArtifact,

        /**
         * Original VCS-related information as defined in the [Package]'s meta-data.
         */
        val vcs: VcsInfo,

        /**
         * Processed VCS-related information about the [Package] that has e.g. common mistakes corrected.
         */
        val vcsProcessed: VcsInfo = vcs.normalize(),

        /**
         * A map that holds arbitrary data. Can be used by third-party tools to add custom data to the model.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val data: CustomData = emptyMap()
) : Comparable<Package> {
    companion object {
        /**
         * A constant for a [Package] where all properties are empty.
         */
        @JvmField
        val EMPTY = Package(
                id = Identifier.EMPTY,
                declaredLicenses = sortedSetOf(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo.EMPTY
        )
    }

    /**
     * A comparison function to sort packages by their identifier.
     */
    override fun compareTo(other: Package) = id.compareTo(other.id)

    /**
     * Compares this package with [other] and creates a [PackageCurationData] containing the values from this package
     * which are different in [other]. All equal values are set to null. Only the fields present in
     * [PackageCurationData] are compared.
     */
    fun diff(other: Package): PackageCurationData {
        require(id == other.id) { "Cannot diff packages with different ids: '$id' vs '${other.id}'" }

        return PackageCurationData(
                declaredLicenses = declaredLicenses.takeIf { it != other.declaredLicenses },
                description = description.takeIf { it != other.description },
                homepageUrl = homepageUrl.takeIf { it != other.homepageUrl },
                binaryArtifact = binaryArtifact.takeIf { it != other.binaryArtifact },
                sourceArtifact = sourceArtifact.takeIf { it != other.sourceArtifact },
                vcs = vcs.takeIf { it != other.vcs }
        )
    }

    /**
     * Create a [CuratedPackage] from this package with an empty list of applied curations.
     */
    fun toCuratedPackage() = CuratedPackage(this, emptyList())

    /**
     * Return a [PackageReference] to refer to this [Package] with optional [dependencies] and [errors].
     */
    fun toReference(linkage: PackageLinkage = PackageLinkage.DYNAMIC,
                    dependencies: SortedSet<PackageReference> = sortedSetOf(), errors: List<OrtIssue> = emptyList()) =
            PackageReference(id, linkage, dependencies, errors)
}
