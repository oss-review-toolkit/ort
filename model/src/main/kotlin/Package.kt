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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

import java.util.SortedSet

import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.SpdxOperator
import org.ossreviewtoolkit.utils.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ProcessedDeclaredLicense

/**
 * A generic descriptor for a software package. It contains all relevant meta-data about a package like the name,
 * version, and how to retrieve the package and its source code. It does not contain information about the package's
 * dependencies, however. This is because at this stage we would only be able to get the declared dependencies, whereas
 * we are interested in the resolved dependencies. Resolved dependencies might differ from declared dependencies due to
 * specified version ranges, or change depending on how the package is used in a project due to the build system's
 * dependency resolution process. For example, if multiple versions of the same package are used in a project, the build
 * system might decide to align on a single version of that package.
 */
@JsonIgnoreProperties(value = ["purl"], allowGetters = true)
data class Package(
    /**
     * The unique identifier of this package. The [id]'s type is the name of the package type or protocol (e.g. "Maven"
     * for a file from a Maven repository).
     */
    val id: Identifier,

    /**
     * An additional identifier in [package URL syntax](https://github.com/package-url/purl-spec).
     */
    val purl: String = id.toPurl(),

    /**
     * The list of licenses the authors have declared for this package. This does not necessarily correspond to the
     * licenses as detected by a scanner. Both need to be taken into account for any conclusions.
     */
    val declaredLicenses: SortedSet<String>,

    /**
     * The declared licenses as [SpdxExpression]. If [declaredLicenses] contains multiple licenses they are
     * concatenated with [SpdxOperator.AND].
     */
    val declaredLicensesProcessed: ProcessedDeclaredLicense = DeclaredLicenseProcessor.process(declaredLicenses),

    /**
     * The concluded license as an [SpdxExpression]. It can be used to correct the license of a package in case the
     * [declaredLicenses] found in the packages metadata or the licenses detected by a scanner do not match reality.
     *
     * ORT itself does not set this field, it needs to be set by the user using a [PackageCuration].
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val concludedLicense: SpdxExpression? = null,

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
     * Indicates whether this [Package] is just meta data, like e.g. Maven BOM artifacts which only define constraints
     * for dependency versions.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val isMetaDataOnly: Boolean = false,

    /**
     * Indicates whether the source code of this [Package] has been modified compared to the original source code,
     * e.g., in case of a fork of an upstream Open Source project.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val isModified: Boolean = false
) : Comparable<Package> {
    companion object {
        /**
         * A constant for a [Package] where all properties are empty.
         */
        @JvmField
        val EMPTY = Package(
            id = Identifier.EMPTY,
            purl = "",
            declaredLicenses = sortedSetOf(),
            declaredLicensesProcessed = ProcessedDeclaredLicense.EMPTY,
            concludedLicense = null,
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY,
            vcsProcessed = VcsInfo.EMPTY
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
        require(id == other.id) {
            "Cannot diff packages with different ids: '${id.toCoordinates()}' vs. '${other.id.toCoordinates()}'"
        }

        return PackageCurationData(
            declaredLicenses = declaredLicenses.takeIf { it != other.declaredLicenses },
            description = description.takeIf { it != other.description },
            homepageUrl = homepageUrl.takeIf { it != other.homepageUrl },
            binaryArtifact = binaryArtifact.takeIf { it != other.binaryArtifact },
            sourceArtifact = sourceArtifact.takeIf { it != other.sourceArtifact },
            vcs = vcs.takeIf { it != other.vcs }?.toCuration(),
            isMetaDataOnly = isMetaDataOnly.takeIf { it != other.isMetaDataOnly }
        )
    }

    /**
     * Check if this package contains any erroneous data.
     */
    fun collectIssues(): List<OrtIssue> =
        declaredLicensesProcessed.unmapped.map { unmappedLicense ->
            OrtIssue(
                severity = Severity.WARNING,
                source = id.toCoordinates(),
                message = "The declared license '$unmappedLicense' could not be mapped to a valid license or " +
                        "parsed as an SPDX expression."
            )
        }

    /**
     * Create a [CuratedPackage] from this package with an empty list of applied curations.
     */
    fun toCuratedPackage() = CuratedPackage(this)

    /**
     * Return a [PackageReference] to refer to this [Package] with optional [dependencies] and [issues].
     */
    fun toReference(
        linkage: PackageLinkage? = null,
        dependencies: SortedSet<PackageReference>? = null,
        issues: List<OrtIssue>? = null
    ): PackageReference {
        var ref = PackageReference(id)

        if (linkage != null) {
            ref = ref.copy(linkage = linkage)
        }

        if (dependencies != null) {
            ref = ref.copy(dependencies = dependencies)
        }

        if (issues != null) {
            ref = ref.copy(issues = issues)
        }

        return ref
    }
}
