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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.plugins.reporters.spdx

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.reporter.LicenseTextProvider
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseException
import org.ossreviewtoolkit.utils.spdx.model.SpdxChecksum
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdx.model.SpdxExternalReference
import org.ossreviewtoolkit.utils.spdx.model.SpdxExtractedLicenseInfo
import org.ossreviewtoolkit.utils.spdx.model.SpdxPackage
import org.ossreviewtoolkit.utils.spdx.model.SpdxPackageVerificationCode
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.spdx.toSpdxId

/**
 * Convert an ORT [Hash] to an [SpdxChecksum], or return null if a conversion is not possible.
 */
private fun Hash.toSpdxChecksum(): SpdxChecksum? =
    // The SPDX checksum algorithm names are simple and assumed to be part of ORT's HashAlgorithm aliases.
    SpdxChecksum.Algorithm.values().find { it.name in algorithm.aliases }?.let {
        SpdxChecksum(
            algorithm = it,
            checksumValue = value
        )
    }

/**
 * Convert an [Identifier]'s coordinates to an SPDX reference ID with the specified [infix] and [suffix].
 */
internal fun Identifier.toSpdxId(infix: String = "Package", suffix: String = ""): String =
    buildString {
        append(SpdxConstants.REF_PREFIX)
        if (infix.isNotEmpty()) append("$infix-")
        append(toCoordinates())
        if (suffix.isNotEmpty()) append("-$suffix")
    }.toSpdxId()

/**
 * Get the text with all Copyright statements associated with the package of the given [id], or return `NONE` if there
 * are no associated Copyright statements.
 */
private fun LicenseInfoResolver.getSpdxCopyrightText(id: Identifier): String {
    val copyrightStatements = resolveLicenseInfo(id).flatMapTo(sortedSetOf()) { it.getCopyrights() }
    if (copyrightStatements.isEmpty()) return SpdxConstants.NONE
    return copyrightStatements.joinToString("\n")
}

/**
 * Return all SPDX external references contained in the metadata of the ORT package.
 */
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

/**
 * An enum for the different types of SPDX packages an ORT package can represent.
 */
internal enum class SpdxPackageType(val infix: String, val suffix: String = "") {
    PROJECT("Project"),
    BINARY_PACKAGE("Package"),
    SOURCE_PACKAGE("Package", "source-artifact"),
    VCS_PACKAGE("Package", "vcs")
}

/**
 * Convert this ORT package to an SPDX package. As an ORT package can hold more metadata about its associated artifacts
 * and origin than an SPDX package, the [type] is used to specify which kind of SPDX package should be created from the
 * respective ORT package metadata. [licenseInfoResolver] is used for license and copyright information, and the
 * optional [scanResult] and [provenance] are used for [type]-specific information.
 */
internal fun Package.toSpdxPackage(
    type: SpdxPackageType,
    licenseInfoResolver: LicenseInfoResolver,
    scanResult: ScanResult? = null,
    provenance: RepositoryProvenance? = null
): SpdxPackage {
    val packageVerificationCode = scanResult.toSpdxPackageVerificationCode()
    return SpdxPackage(
        spdxId = id.toSpdxId(type.infix, type.suffix),
        checksums = when (type) {
            SpdxPackageType.BINARY_PACKAGE -> listOfNotNull(binaryArtifact.hash.toSpdxChecksum())
            SpdxPackageType.SOURCE_PACKAGE -> listOfNotNull(sourceArtifact.hash.toSpdxChecksum())
            else -> emptyList()
        },
        copyrightText = licenseInfoResolver.getSpdxCopyrightText(id),
        downloadLocation = when (type) {
            SpdxPackageType.PROJECT -> SpdxConstants.NONE
            SpdxPackageType.BINARY_PACKAGE -> binaryArtifact.url.nullOrBlankToSpdxNone()
            SpdxPackageType.SOURCE_PACKAGE -> sourceArtifact.url.nullOrBlankToSpdxNone()
            SpdxPackageType.VCS_PACKAGE -> vcsProcessed.toSpdxDownloadLocation(provenance?.resolvedRevision)
        },
        externalRefs = if (type == SpdxPackageType.PROJECT) emptyList() else toSpdxExternalReferences(),
        filesAnalyzed = packageVerificationCode != null,
        homepage = homepageUrl.nullOrBlankToSpdxNone(),
        licenseConcluded = when (type) {
            // Clear the concluded license as it might need to be different for the source artifact.
            SpdxPackageType.SOURCE_PACKAGE -> SpdxConstants.NOASSERTION
            // Clear the concluded license as it might need to be different for the VCS location.
            SpdxPackageType.VCS_PACKAGE -> SpdxConstants.NOASSERTION
            else -> concludedLicense.nullOrBlankToSpdxNoassertionOrNone()
        },
        licenseDeclared = declaredLicensesProcessed.toSpdxDeclaredLicense(),
        licenseInfoFromFiles = licenseInfoResolver.resolveLicenseInfo(id)
            .filterExcluded()
            .filter(LicenseView.ONLY_DETECTED)
            .map { resolvedLicense ->
                resolvedLicense.license.takeIf { it.isValid(SpdxExpression.Strictness.ALLOW_DEPRECATED) }
                    .nullOrBlankToSpdxNoassertionOrNone()
            }
            .distinct()
            .sorted(),
        packageVerificationCode = packageVerificationCode,
        name = id.name,
        summary = description.nullOrBlankToSpdxNone(),
        versionInfo = id.version
    )
}

/**
 * Convert processed declared licenses to SPDX. Unmapped licenses are represented as `NOASSERTION`.
 */
private fun ProcessedDeclaredLicense.toSpdxDeclaredLicense(): String =
    when {
        // If there are unmapped licenses, represent this by adding NOASSERTION.
        unmapped.isNotEmpty() -> {
            spdxExpression?.let {
                if (SpdxConstants.NOASSERTION !in it.licenses()) {
                    (it and SpdxConstants.NOASSERTION.toSpdx()).toString()
                } else {
                    it.toString()
                }
            } ?: SpdxConstants.NOASSERTION
        }

        else -> spdxExpression.nullOrBlankToSpdxNoassertionOrNone()
    }

/**
 * Wrap a scan summary's package verification code in a [SpdxPackageVerificationCode].
 */
internal fun ScanResult?.toSpdxPackageVerificationCode(): SpdxPackageVerificationCode? =
    this?.summary?.packageVerificationCode?.takeUnless { it.isEmpty() }?.let {
        SpdxPackageVerificationCode(packageVerificationCodeValue = it)
    }

/**
 * Use [licenseTextProvider] to add the license texts for all packages to the [SpdxDocument].
 */
internal fun SpdxDocument.addExtractedLicenseInfo(licenseTextProvider: LicenseTextProvider): SpdxDocument {
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

/**
 * Convert an [SpdxExpression] to `NOASSERTION` if null, to `NONE` if blank, or to its string representation otherwise.
 */
private fun SpdxExpression?.nullOrBlankToSpdxNoassertionOrNone(): String =
    when {
        this == null -> SpdxConstants.NOASSERTION
        toString().isBlank() -> SpdxConstants.NONE
        else -> toString()
    }

/**
 * Convert a null or blank [String] to `NONE`.
 */
internal fun String?.nullOrBlankToSpdxNone(): String = if (isNullOrBlank()) SpdxConstants.NONE else this

/**
 * Create an SPDX download location string from [VcsInfo] and an optional [resolvedRevision].
 */
internal fun VcsInfo.toSpdxDownloadLocation(resolvedRevision: String?): String {
    val vcsTool = when (type) {
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
