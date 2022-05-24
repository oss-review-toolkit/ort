/*
 * Copyright (C) 2020-2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.spdx

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.reporter.LicenseTextProvider
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.REF_PREFIX
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseException
import org.ossreviewtoolkit.utils.spdx.model.SpdxCreationInfo
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdx.model.SpdxExternalReference
import org.ossreviewtoolkit.utils.spdx.model.SpdxExtractedLicenseInfo
import org.ossreviewtoolkit.utils.spdx.model.SpdxPackage
import org.ossreviewtoolkit.utils.spdx.model.SpdxPackageVerificationCode
import org.ossreviewtoolkit.utils.spdx.model.SpdxRelationship
import org.ossreviewtoolkit.utils.spdx.toSpdxId

/**
 * A class for mapping [OrtResult]s to [SpdxDocument]s.
 */
object SpdxDocumentModelMapper {
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

        val projectPackages = ortResult.getProjects(omitExcluded = true, includeSubProjects = false).map { project ->
            val spdxProjectPackage = project.toPackage().toSpdxPackage(licenseInfoResolver, isProject = true)

            ortResult.collectDependencies(project.id, 1).mapTo(relationships) { dependency ->
                SpdxRelationship(
                    spdxElementId = spdxProjectPackage.spdxId,
                    relationshipType = SpdxRelationship.Type.DEPENDS_ON,
                    relatedSpdxElement = dependency.toSpdxId("Package")
                )
            }

            spdxProjectPackage
        }

        ortResult.getPackages(omitExcluded = true).forEach { curatedPackage ->
            val pkg = curatedPackage.pkg
            val binaryPackage = pkg.toSpdxPackage(licenseInfoResolver)

            ortResult.collectDependencies(pkg.id, 1).mapTo(relationships) { dependency ->
                SpdxRelationship(
                    spdxElementId = binaryPackage.spdxId,
                    relationshipType = SpdxRelationship.Type.DEPENDS_ON,
                    relatedSpdxElement = dependency.toSpdxId("Package")
                )
            }

            packages += binaryPackage

            if (pkg.vcsProcessed.url.isNotBlank()) {
                val vcsScanResult =
                    ortResult.getScanResultsForId(curatedPackage.pkg.id).find { it.provenance is RepositoryProvenance }
                val provenance = vcsScanResult?.provenance as? RepositoryProvenance

                val (filesAnalyzed, packageVerificationCode) =
                    if (vcsScanResult?.summary?.packageVerificationCode?.isNotEmpty() == true) {
                        true to vcsScanResult.toSpdxPackageVerificationCode()
                    } else {
                        false to null
                    }

                // TODO: The copyright text contains copyrights from all scan results.
                val vcsPackage = binaryPackage.copy(
                    spdxId = "${binaryPackage.spdxId}-vcs",
                    filesAnalyzed = filesAnalyzed,
                    downloadLocation = pkg.vcsProcessed.toSpdxDownloadLocation(provenance?.resolvedRevision),
                    licenseConcluded = SpdxConstants.NOASSERTION,
                    licenseDeclared = SpdxConstants.NOASSERTION,
                    packageVerificationCode = packageVerificationCode
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
                val sourceArtifactScanResult =
                    ortResult.getScanResultsForId(curatedPackage.pkg.id).find { it.provenance is ArtifactProvenance }

                val (filesAnalyzed, packageVerificationCode) =
                    if (sourceArtifactScanResult?.summary?.packageVerificationCode?.isNotEmpty() == true) {
                        true to sourceArtifactScanResult.toSpdxPackageVerificationCode()
                    } else {
                        false to null
                    }

                // TODO: The copyright text contains copyrights from all scan results.
                val sourceArtifactPackage = binaryPackage.copy(
                    spdxId = "${binaryPackage.spdxId}-source-artifact",
                    filesAnalyzed = filesAnalyzed,
                    downloadLocation = curatedPackage.pkg.sourceArtifact.url.nullOrBlankToSpdxNone(),
                    licenseConcluded = SpdxConstants.NOASSERTION,
                    licenseDeclared = SpdxConstants.NOASSERTION,
                    packageVerificationCode = packageVerificationCode
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

private fun getSpdxCopyrightText(
    licenseInfoResolver: LicenseInfoResolver,
    id: Identifier
): String {
    val copyrightStatements = licenseInfoResolver.resolveLicenseInfo(id).flatMapTo(sortedSetOf()) { it.getCopyrights() }

    return if (copyrightStatements.isNotEmpty()) {
        copyrightStatements.joinToString("\n")
    } else {
        SpdxConstants.NONE
    }
}

/**
 * Convert an [Identifier]'s coordinates to an SPDX reference ID with the specified [infix].
 */
private fun Identifier.toSpdxId(infix: String) = "$REF_PREFIX$infix-${toCoordinates()}".toSpdxId()

private fun Package.toSpdxExternalReferences(): List<SpdxExternalReference> {
    val externalRefs = mutableListOf<SpdxExternalReference>()

    if (purl.isNotEmpty()) {
        externalRefs += SpdxExternalReference(
            referenceType = SpdxExternalReference.Type.Purl,
            referenceLocator = purl
        )
    }

    cpe?.takeUnless { it.isEmpty() }?.let {
        val referenceType = if (it.startsWith("cpe:2.3")) {
            SpdxExternalReference.Type.Cpe23Type
        } else {
            SpdxExternalReference.Type.Cpe22Type
        }
        externalRefs += SpdxExternalReference(
            referenceType,
            referenceLocator = it
        )
    }

    return externalRefs
}

private fun Package.toSpdxPackage(licenseInfoResolver: LicenseInfoResolver, isProject: Boolean = false) =
    SpdxPackage(
        spdxId = id.toSpdxId(if (isProject) "Project" else "Package"),
        copyrightText = getSpdxCopyrightText(licenseInfoResolver, id),
        downloadLocation = binaryArtifact.url.nullOrBlankToSpdxNone(),
        externalRefs = if (isProject) emptyList() else toSpdxExternalReferences(),
        filesAnalyzed = false,
        homepage = homepageUrl.nullOrBlankToSpdxNone(),
        licenseConcluded = concludedLicense.nullOrBlankToSpdxNoassertionOrNone(),
        licenseDeclared = declaredLicensesProcessed.toSpdxDeclaredLicense(),
        name = id.name,
        summary = description.nullOrBlankToSpdxNone(),
        versionInfo = id.version
    )

private fun ProcessedDeclaredLicense.toSpdxDeclaredLicense(): String =
    when {
        unmapped.isEmpty() -> spdxExpression.nullOrBlankToSpdxNoassertionOrNone()
        spdxExpression == null -> SpdxConstants.NOASSERTION
        spdxExpression.toString().isBlank() -> SpdxConstants.NOASSERTION
        spdxExpression.toString() == SpdxConstants.NONE -> SpdxConstants.NOASSERTION
        else -> spdxExpression.toString()
    }

private fun String?.nullOrBlankToSpdxNone(): String = if (isNullOrBlank()) SpdxConstants.NONE else this

private fun ScanResult.toSpdxPackageVerificationCode(): SpdxPackageVerificationCode =
    SpdxPackageVerificationCode(
        packageVerificationCodeExcludedFiles = emptyList(),
        packageVerificationCodeValue = summary.packageVerificationCode
    )

private fun SpdxDocument.addExtractedLicenseInfo(licenseTextProvider: LicenseTextProvider): SpdxDocument {
    val nonSpdxLicenses = packages.flatMapTo(mutableSetOf()) {
        // TODO: Also add detected non-SPDX licenses here.
        SpdxExpression.parse(it.licenseConcluded).licenses() + SpdxExpression.parse(it.licenseDeclared).licenses()
    }.filter {
        SpdxConstants.isPresent(it) && SpdxLicense.forId(it) == null && SpdxLicenseException.forId(it) == null
    }

    val extractedLicenseInfo = nonSpdxLicenses.sorted().mapNotNull { license ->
        licenseTextProvider.getLicenseText(license)?.let { text ->
            SpdxExtractedLicenseInfo(
                licenseId = license,
                extractedText = text
            )
        }
    }

    return copy(hasExtractedLicensingInfos = extractedLicenseInfo)
}

private fun SpdxExpression?.nullOrBlankToSpdxNoassertionOrNone(): String =
    when {
        this == null -> SpdxConstants.NOASSERTION
        toString().isBlank() -> SpdxConstants.NONE
        else -> toString()
    }

private fun VcsInfo.toSpdxDownloadLocation(resolvedRevision: String?): String {
    val vcsTool = when (type) {
        VcsType.CVS -> "cvs"
        VcsType.GIT -> "git"
        VcsType.GIT_REPO -> "repo"
        VcsType.MERCURIAL -> "hg"
        VcsType.SUBVERSION -> "svn"
        else -> type.toString().lowercase()
    }

    return buildString {
        append(vcsTool)
        if (vcsTool.isNotEmpty()) append("+")
        append(url.replaceCredentialsInUri())
        if (!resolvedRevision.isNullOrBlank()) append("@$resolvedRevision")
        if (path.isNotBlank()) append("#$path")
    }
}
