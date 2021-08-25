/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

import java.util.SortedSet

import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.DeclaredLicenseProcessor

/**
 * This class contains curation data for a package. It is used to amend the automatically detected metadata for a
 * package with corrections. This is required because the metadata provided by a package can be wrong (e.g. outdated
 * VCS data) or incomplete.
 */
@JsonIgnoreProperties(value = [/* Backwards-compatibility: */ "declared_licenses"])
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PackageCurationData(
    /**
     * A plain-text comment about this curation. Should contain information about how and why the curation was
     * created.
     */
    val comment: String? = null,

    /**
     * The list of authors of this package.
     */
    val authors: SortedSet<String>? = null,

    /**
     * The concluded license as an [SpdxExpression]. It can be used to correct the [declared licenses of a package]
     * [Package.declaredLicenses] in case the found in the packages metadata or the licenses detected by a scanner do
     * not match reality.
     */
    val concludedLicense: SpdxExpression? = null,

    /**
     * The description of the package, as provided by the package manager.
     */
    val description: String? = null,

    /**
     * The homepage of the package.
     */
    val homepageUrl: String? = null,

    /**
     * The remote artifact where the binary package can be downloaded.
     */
    val binaryArtifact: RemoteArtifact? = null,

    /**
     * The remote artifact where the source package can be downloaded.
     */
    val sourceArtifact: RemoteArtifact? = null,

    /**
     * VCS-related information.
     */
    val vcs: VcsInfoCurationData? = null,

    /**
     * Whether the package is metadata only.
     */
    val isMetaDataOnly: Boolean? = null,

    /**
     * Whether the package is modified compared to the original source
     */
    val isModified: Boolean? = null,

    /**
     * The declared license mapping entries to be added to the actual declared license mapping, which in turn gets
     * applied by [DeclaredLicenseProcessor.process].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val declaredLicenseMapping: Map<String, SpdxExpression> = emptyMap()
) {
    /**
     * Apply the curation data to the provided [base] package by overriding all values of the original package with
     * non-null values of the curation data, and return the curated package.
     */
    fun apply(base: CuratedPackage): CuratedPackage = applyCurationToPackage(base, this)
}

private fun applyCurationToPackage(targetPackage: CuratedPackage, curation: PackageCurationData): CuratedPackage {
    val base = targetPackage.pkg

    val vcs = curation.vcs?.let {
        // Curation data for VCS information is handled specially so we can curate only individual properties.
        VcsInfo(
            type = it.type ?: base.vcs.type,
            url = it.url ?: base.vcs.url,
            revision = it.revision ?: base.vcs.revision,
            path = it.path ?: base.vcs.path
        )
    } ?: base.vcs

    val authors = curation.authors ?: base.authors
    val declaredLicenseMapping = targetPackage.getDeclaredLicenseMapping() + curation.declaredLicenseMapping
    val declaredLicensesProcessed = DeclaredLicenseProcessor.process(base.declaredLicenses, declaredLicenseMapping)

    val pkg = Package(
        id = base.id,
        authors = authors,
        declaredLicenses = base.declaredLicenses,
        declaredLicensesProcessed = declaredLicensesProcessed,
        concludedLicense = curation.concludedLicense ?: base.concludedLicense,
        description = curation.description ?: base.description,
        homepageUrl = curation.homepageUrl ?: base.homepageUrl,
        binaryArtifact = curation.binaryArtifact ?: base.binaryArtifact,
        sourceArtifact = curation.sourceArtifact ?: base.sourceArtifact,
        vcs = vcs,
        isMetaDataOnly = curation.isMetaDataOnly ?: base.isMetaDataOnly,
        isModified = curation.isModified ?: base.isModified
    )

    val declaredLicenseMappingDiff = mutableMapOf<String, SpdxExpression>().apply {
        val previous = targetPackage.getDeclaredLicenseMapping().toList()
        val current = declaredLicenseMapping.toList()

        putAll(previous - current)
    }

    val curations = targetPackage.curations + PackageCurationResult(
        base = base.diff(pkg).copy(declaredLicenseMapping = declaredLicenseMappingDiff),
        curation = curation
    )

    return CuratedPackage(pkg, curations)
}
