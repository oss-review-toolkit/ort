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

import org.ossreviewtoolkit.utils.common.zip
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxExpression.Strictness.ALLOW_LICENSEREF_EXCEPTIONS
import org.ossreviewtoolkit.utils.spdx.toExpression

/**
 * This class contains curation data for a package. It is used to amend the automatically detected metadata for a
 * package with corrections. This is required because the metadata provided by a package can be wrong (e.g. outdated
 * VCS data) or incomplete.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PackageCurationData(
    /**
     * A plain-text comment about this curation. Should contain information about how and why the curation was
     * created.
     */
    val comment: String? = null,

    /**
     * An additional identifier in [package URL syntax](https://github.com/package-url/purl-spec).
     */
    val purl: String? = null,

    /**
     * An additional identifier in [CPE syntax](https://cpe.mitre.org/specification/).
     */
    val cpe: String? = null,

    /**
     * The set of authors of the package.
     */
    val authors: Set<String>? = null,

    /**
     * The concluded license as an [SpdxExpression]. It can be used to override the [declared][Package.declaredLicenses]
     * / [detected][LicenseFinding.license] licenses of the package.
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
    val isMetadataOnly: Boolean? = null,

    /**
     * Whether the package is modified compared to the original source
     */
    val isModified: Boolean? = null,

    /**
     * The declared license mapping entries to be added to the actual declared license mapping, which in turn gets
     * applied by [DeclaredLicenseProcessor.process].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val declaredLicenseMapping: Map<String, SpdxExpression> = emptyMap(),

    /**
     * The considered source code origins in order of priority. If not null, this must not be empty and not contain any
     * duplicates.
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
    init {
        declaredLicenseMapping.forEach { (key, value) ->
            require(value.isValid(ALLOW_LICENSEREF_EXCEPTIONS)) {
                "The declared license '$key' is configured to map to '$value' which is not a valid SPDX expression."
            }
        }
    }

    /**
     * Apply this [PackageCurationData] to [basePackage] by overriding all values of [basePackage] with non-null values
     * and return the resulting [CuratedPackage].
     */
    fun apply(basePackage: CuratedPackage): CuratedPackage {
        val base = basePackage.metadata

        val vcsProcessed = vcs?.let {
            // Curation data for VCS information is handled specially, so we can curate only individual properties.
            VcsInfo(
                type = it.type ?: base.vcsProcessed.type,
                url = it.url ?: base.vcsProcessed.url,
                revision = it.revision ?: base.vcsProcessed.revision,
                path = it.path ?: base.vcsProcessed.path
            ).normalize()
        } ?: base.vcsProcessed

        val declaredLicenseMapping = basePackage.getDeclaredLicenseMapping() + declaredLicenseMapping
        val declaredLicensesProcessed = DeclaredLicenseProcessor.process(
            base.declaredLicenses,
            declaredLicenseMapping
        )

        val pkg = Package(
            id = base.id,
            purl = purl ?: base.purl,
            cpe = cpe ?: base.cpe,
            authors = authors ?: base.authors,
            declaredLicenses = base.declaredLicenses,
            declaredLicensesProcessed = declaredLicensesProcessed,
            concludedLicense = concludedLicense ?: base.concludedLicense,
            description = description ?: base.description,
            homepageUrl = homepageUrl ?: base.homepageUrl,
            binaryArtifact = binaryArtifact ?: base.binaryArtifact,
            sourceArtifact = sourceArtifact ?: base.sourceArtifact,
            vcs = base.vcs,
            vcsProcessed = vcsProcessed,
            isMetadataOnly = isMetadataOnly ?: base.isMetadataOnly,
            isModified = isModified ?: base.isModified,
            sourceCodeOrigins = sourceCodeOrigins ?: base.sourceCodeOrigins,
            labels = base.labels + labels
        )

        return CuratedPackage(pkg, basePackage.curations + this)
    }

    /**
     * Merge with [other] curation data. The `comment` properties are joined but probably need to be adjusted before
     * further processing as their meaning might have been distorted by the merge. For properties that cannot be merged,
     * data in this instance has precedence over data in the other instance.
     */
    fun merge(other: PackageCurationData) =
        PackageCurationData(
            comment = setOfNotNull(comment, other.comment).joinToString("\n").takeIf { it.isNotEmpty() },
            purl = purl ?: other.purl,
            cpe = cpe ?: other.cpe,
            authors = authors.orEmpty() + other.authors.orEmpty(),
            concludedLicense = setOfNotNull(concludedLicense, other.concludedLicense).toExpression(),
            description = description ?: other.description,
            homepageUrl = homepageUrl ?: other.homepageUrl,
            binaryArtifact = binaryArtifact ?: other.binaryArtifact,
            sourceArtifact = sourceArtifact ?: other.sourceArtifact,
            vcs = vcs?.merge(other.vcs ?: vcs) ?: other.vcs,
            isMetadataOnly = isMetadataOnly ?: other.isMetadataOnly,
            isModified = isModified ?: other.isModified,
            declaredLicenseMapping = declaredLicenseMapping.zip(other.declaredLicenseMapping) { value, _ ->
                value
            },
            sourceCodeOrigins = sourceCodeOrigins ?: other.sourceCodeOrigins,
            labels = labels + other.labels
        )
}

private fun CuratedPackage.getDeclaredLicenseMapping(): Map<String, SpdxExpression> =
    buildMap {
        curations.forEach { putAll(it.declaredLicenseMapping) }
    }
