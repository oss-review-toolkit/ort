/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.spdx

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.reporter.LicenseTextProvider
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.model.SpdxCreationInfo
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdx.model.SpdxPackage
import org.ossreviewtoolkit.utils.spdx.model.SpdxRelationship

/**
 * A class for mapping [OrtResult]s to [SpdxDocument]s.
 */
internal object SpdxDocumentModelMapper {
    data class SpdxDocumentParams(
        val documentName: String,
        val documentComment: String,
        val creationInfoComment: String
    )

    fun map(
        ortResult: OrtResult,
        licenseInfoResolver: LicenseInfoResolver,
        licenseTextProvider: LicenseTextProvider,
        params: SpdxDocumentParams
    ): SpdxDocument {
        val packages = mutableListOf<SpdxPackage>()
        val relationships = mutableListOf<SpdxRelationship>()

        val projects = ortResult.getProjects(omitExcluded = true, includeSubProjects = false).sortedBy { it.id }
        val projectPackages = projects.map { project ->
            val spdxProjectPackage = project.toPackage().toSpdxPackage(SpdxPackageType.PROJECT, licenseInfoResolver)

            ortResult.getDependencies(project.id, 1).mapTo(relationships) { dependency ->
                SpdxRelationship(
                    spdxElementId = spdxProjectPackage.spdxId,
                    relationshipType = SpdxRelationship.Type.DEPENDS_ON,
                    relatedSpdxElement = dependency.toSpdxId()
                )
            }

            spdxProjectPackage
        }

        ortResult.getPackages(omitExcluded = true).sortedBy { it.metadata.id }.forEach { curatedPackage ->
            val pkg = curatedPackage.metadata
            val binaryPackage = pkg.toSpdxPackage(SpdxPackageType.BINARY_PACKAGE, licenseInfoResolver)

            ortResult.getDependencies(pkg.id, 1).mapTo(relationships) { dependency ->
                SpdxRelationship(
                    spdxElementId = binaryPackage.spdxId,
                    relationshipType = SpdxRelationship.Type.DEPENDS_ON,
                    relatedSpdxElement = dependency.toSpdxId()
                )
            }

            packages += binaryPackage

            if (pkg.vcsProcessed.url.isNotBlank()) {
                val vcsScanResult = ortResult.getScanResultsForId(curatedPackage.metadata.id).find {
                    it.provenance is RepositoryProvenance
                }
                val provenance = vcsScanResult?.provenance as? RepositoryProvenance

                // TODO: The copyright text contains copyrights from all scan results.
                val vcsPackage = pkg.toSpdxPackage(
                    SpdxPackageType.VCS_PACKAGE,
                    licenseInfoResolver,
                    vcsScanResult,
                    provenance
                )

                val vcsPackageRelationShip = SpdxRelationship(
                    spdxElementId = binaryPackage.spdxId,
                    relationshipType = SpdxRelationship.Type.GENERATED_FROM,
                    relatedSpdxElement = vcsPackage.spdxId
                )

                packages += vcsPackage
                relationships += vcsPackageRelationShip
            }

            if (pkg.sourceArtifact.url.isNotBlank()) {
                val sourceArtifactScanResult = ortResult.getScanResultsForId(curatedPackage.metadata.id).find {
                    it.provenance is ArtifactProvenance
                }

                // TODO: The copyright text contains copyrights from all scan results.
                val sourceArtifactPackage = pkg.toSpdxPackage(
                    SpdxPackageType.SOURCE_PACKAGE,
                    licenseInfoResolver,
                    sourceArtifactScanResult
                )

                val sourceArtifactPackageRelationship = SpdxRelationship(
                    spdxElementId = binaryPackage.spdxId,
                    relationshipType = SpdxRelationship.Type.GENERATED_FROM,
                    relatedSpdxElement = sourceArtifactPackage.spdxId
                )

                packages += sourceArtifactPackage
                relationships += sourceArtifactPackageRelationship
            }
        }

        return SpdxDocument(
            comment = params.documentComment,
            creationInfo = SpdxCreationInfo(
                comment = params.creationInfoComment,
                created = Instant.now().truncatedTo(ChronoUnit.SECONDS),
                creators = listOf("${SpdxConstants.TOOL}$ORT_FULL_NAME - ${Environment.ORT_VERSION}"),
                licenseListVersion = SpdxLicense.LICENSE_LIST_VERSION.substringBefore("-")
            ),
            documentNamespace = "spdx://${UUID.randomUUID()}",
            documentDescribes = projectPackages.map { it.spdxId },
            name = params.documentName,
            packages = projectPackages + packages,
            relationships = relationships.sortedBy { it.spdxElementId }
        ).addExtractedLicenseInfo(licenseTextProvider)
    }
}
