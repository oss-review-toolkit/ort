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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.annotation.JsonSerialize

import org.ossreviewtoolkit.model.utils.requireNotEmptyNoDuplicates
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.ort.StringSortedSetConverter
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxOperator

/**
 * A generic descriptor for a software package. It contains all relevant metadata about a package like the name,
 * version, and how to retrieve the package and its source code. It does not contain information about the package's
 * dependencies, however. This is because at this stage ORT would only be able to get the declared dependencies, whereas
 * the resolved dependencies are of interest. Resolved dependencies might differ from declared dependencies due to
 * specified version ranges, or change depending on how the package is used in a project due to the build system's
 * dependency resolution process. For example, if multiple versions of the same package are used in a project, the build
 * system might decide to align on a single version of that package.
 */
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
     * An optional additional identifier in [CPE syntax](https://cpe.mitre.org/specification/).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val cpe: String? = null,

    /**
     * The set of authors declared for this package.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(converter = StringSortedSetConverter::class)
    val authors: Set<String> = emptySet(),

    /**
     * The set of licenses declared for this package. This does not necessarily correspond to the licenses as detected
     * by a scanner. Both need to be taken into account for any conclusions.
     */
    @JsonSerialize(converter = StringSortedSetConverter::class)
    val declaredLicenses: Set<String>,

    /**
     * The declared licenses as [SpdxExpression]. If [declaredLicenses] contains multiple licenses they are
     * concatenated with [SpdxOperator.AND].
     */
    val declaredLicensesProcessed: ProcessedDeclaredLicense = DeclaredLicenseProcessor.process(declaredLicenses),

    /**
     * The concluded license as an [SpdxExpression]. It can be used to override the [declared][declaredLicenses] /
     * [detected][LicenseFinding.license] licenses of a package.
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
     * Original VCS-related information as defined in the package's metadata.
     */
    val vcs: VcsInfo,

    /**
     * Processed VCS-related information about the package in normalized form. The information is either derived from
     * [vcs], guessed from additional data as a fallback, or empty. On top of that [PackageCuration]s may have been
     * applied.
     */
    val vcsProcessed: VcsInfo = vcs.normalize(),

    /**
     * Indicates whether the package is just metadata, like e.g. Maven BOM artifacts which only define constraints
     * for dependency versions.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val isMetadataOnly: Boolean = false,

    /**
     * Indicates whether the source code of the package has been modified compared to the original source code,
     * e.g., in case of a fork of an upstream Open Source project.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val isModified: Boolean = false,

    /**
     * The considered source code origins and their priority order to use for this package. If null, the configured
     * default is used. If not null, this must not be empty and not contain any duplicates.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val sourceCodeOrigins: List<SourceCodeOrigin>? = null,

    /**
     * User defined labels associated with this package. The labels are not interpreted by the core of ORT itself, but
     * can be used in parts of ORT such as plugins, in evaluator rules, or in reporter templates.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyOrder(alphabetic = true)
    val labels: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * A constant for a [Package] where all properties are empty.
         */
        @JvmField
        val EMPTY = Package(
            id = Identifier.EMPTY,
            purl = "",
            authors = emptySet(),
            declaredLicenses = emptySet(),
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

    init {
        sourceCodeOrigins?.requireNotEmptyNoDuplicates()
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
        dependencies: Set<PackageReference>? = null,
        issues: List<Issue>? = null
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
