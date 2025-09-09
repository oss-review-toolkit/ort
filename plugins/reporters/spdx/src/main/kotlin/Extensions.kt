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

import java.util.concurrent.atomic.AtomicInteger

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.licenses.Findings
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.utils.FindingCurationMatcher
import org.ossreviewtoolkit.model.utils.prependedPath
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProvider
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.calculatePackageVerificationCode
import org.ossreviewtoolkit.utils.spdx.nullOrBlankToSpdxNoassertionOrNone
import org.ossreviewtoolkit.utils.spdx.toSpdxId
import org.ossreviewtoolkit.utils.spdxdocument.model.SPDX_VERSION_2_2
import org.ossreviewtoolkit.utils.spdxdocument.model.SPDX_VERSION_2_3
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxChecksum
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxExternalReference
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxExtractedLicenseInfo
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxFile
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxPackage
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxPackageVerificationCode

/**
 * Convert an ORT [Hash] to an [SpdxChecksum], or return null if a conversion is not possible.
 */
private fun Hash.toSpdxChecksum(): SpdxChecksum? =
    SpdxChecksum.Algorithm.entries
        // The SPDX checksum algorithm names are simple and assumed to be part of ORT's HashAlgorithm aliases.
        .find { it.name in algorithm.aliases }
        ?.let {
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
 * Convert an [Identifier]'s coordinates to an SPDX reference ID for the specified [type].
 */
internal fun Identifier.toSpdxId(type: SpdxPackageType): String = toSpdxId(type.infix, type.suffix)

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
 * respective ORT package metadata.
 */
internal fun Package.toSpdxPackage(
    type: SpdxPackageType,
    licenseInfoResolver: LicenseInfoResolver,
    ortResult: OrtResult
): SpdxPackage {
    val packageVerificationCode = ortResult.getPackageVerificationCode(id, type)?.let {
        SpdxPackageVerificationCode(packageVerificationCodeValue = it)
    }

    val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(id).filterExcluded()
        .applyChoices(ortResult.getPackageLicenseChoices(id))
        .applyChoices(ortResult.getRepositoryLicenseChoices())

    return SpdxPackage(
        spdxId = id.toSpdxId(type),
        checksums = when (type) {
            SpdxPackageType.BINARY_PACKAGE -> listOfNotNull(binaryArtifact.hash.toSpdxChecksum())
            SpdxPackageType.SOURCE_PACKAGE -> listOfNotNull(sourceArtifact.hash.toSpdxChecksum())
            else -> emptyList()
        },
        copyrightText = licenseInfoResolver.getSpdxCopyrightText(id),
        description = description,
        downloadLocation = when (type) {
            SpdxPackageType.BINARY_PACKAGE -> binaryArtifact.url.nullOrBlankToSpdxNone()
            SpdxPackageType.SOURCE_PACKAGE -> sourceArtifact.url.nullOrBlankToSpdxNone()
            SpdxPackageType.VCS_PACKAGE -> vcsProcessed.toSpdxDownloadLocation(ortResult.getResolvedRevision(id))
            SpdxPackageType.PROJECT -> vcs.url.nullOrBlankToSpdxNone()
        },
        externalRefs = if (type == SpdxPackageType.PROJECT) emptyList() else toSpdxExternalReferences(),
        filesAnalyzed = packageVerificationCode != null,
        homepage = homepageUrl.nullOrBlankToSpdxNone(),
        licenseConcluded = when (type) {
            // Clear the concluded license as it might need to be different for the source artifact.
            SpdxPackageType.SOURCE_PACKAGE -> SpdxConstants.NOASSERTION
            // Clear the concluded license as it might need to be different for the VCS location.
            SpdxPackageType.VCS_PACKAGE -> SpdxConstants.NOASSERTION
            SpdxPackageType.PROJECT -> concludedLicense.nullOrBlankToSpdxNoassertionOrNone()
            else -> concludedLicense.nullOrBlankToSpdxNoassertionOrNone()
        },
        licenseDeclared = resolvedLicenseInfo.mainLicense()
            ?.simplify()
            ?.sorted()
            ?.nullOrBlankToSpdxNoassertionOrNone()
            ?: SpdxConstants.NONE,
        licenseInfoFromFiles = if (packageVerificationCode == null) {
            emptyList()
        } else {
            resolvedLicenseInfo.filter(LicenseView.ONLY_DETECTED)
                .mapTo(mutableSetOf()) { it.license.nullOrBlankToSpdxNoassertionOrNone() }
                .sorted()
        },
        name = id.name,
        originator = authors.takeUnless { it.isEmpty() }?.joinToString(prefix = "${SpdxConstants.PERSON} "),
        packageVerificationCode = packageVerificationCode,
        supplier = authors.takeUnless { it.isEmpty() }?.joinToString(prefix = "${SpdxConstants.PERSON} "),
        versionInfo = id.version
    ).also { spdxPackage ->
        runCatching {
            spdxPackage.validate()
        }.onFailure {
            logger.error { "Validation failed for '${spdxPackage.spdxId}': ${it.message}" }
        }
    }
}

private fun OrtResult.getVcsScanResult(id: Identifier): ScanResult? =
    getScanResultsForId(id).firstOrNull { it.provenance is RepositoryProvenance }

private fun OrtResult.getResolvedRevision(id: Identifier): String? =
    (getVcsScanResult(id)?.provenance as RepositoryProvenance?)?.resolvedRevision

private fun OrtResult.getPackageVerificationCode(id: Identifier, type: SpdxPackageType): String? =
    when (type) {
        SpdxPackageType.VCS_PACKAGE -> getFileListForId(id).takeIf { it?.provenance is RepositoryProvenance }
        SpdxPackageType.SOURCE_PACKAGE -> getFileListForId(id).takeIf { it?.provenance is ArtifactProvenance }
        SpdxPackageType.PROJECT -> getFileListForId(id).takeIf { it?.provenance is KnownProvenance }
        else -> null
    }?.let { fileList ->
        calculatePackageVerificationCode(fileList.files.map { it.sha1 }.asSequence())
    }

/**
 * Use [licenseTextProvider] to add the license texts for all packages to the [SpdxDocument].
 */
internal fun SpdxDocument.addExtractedLicenseInfo(licenseFactProvider: LicenseFactProvider): SpdxDocument {
    val allLicenses = buildSet {
        packages.forEach {
            add(it.licenseConcluded)
            add(it.licenseDeclared)
            addAll(it.licenseInfoFromFiles)
        }
    }.flatMapTo(mutableSetOf()) { SpdxExpression.parse(it).licenses() }

    val nonSpdxLicenses = allLicenses.filter { SpdxConstants.isPresent(it) && SpdxLicense.forId(it) == null }

    val extractedLicenseInfo = nonSpdxLicenses.sorted().mapNotNull { license ->
        licenseFactProvider.getLicenseText(license)?.takeIf { it.isNotBlank() }?.let { text ->
            SpdxExtractedLicenseInfo(
                licenseId = license,
                extractedText = text
            )
        }
    }

    return copy(hasExtractedLicensingInfos = extractedLicenseInfo)
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

/**
 * Return the [SpdxFile]s for the package denoted by [id]. The result contains exactly those file residing in the
 * artifact or repository corresponding to [sourceCodeOrigin], which do contain at least one license or copyright
 * finding. The given [nextFileIndex] is incremented by one for each returned file, and it's respective value is used as
 * a part of the generated SPDX ID of the files.
 */
internal fun OrtResult.getSpdxFiles(
    id: Identifier,
    licenseInfoResolver: LicenseInfoResolver,
    sourceCodeOrigin: SourceCodeOrigin,
    nextFileIndex: AtomicInteger
): List<SpdxFile> =
    getFileFindings(id, licenseInfoResolver, sourceCodeOrigin).orEmpty().sortedBy { it.path }.map { fileFindings ->
        SpdxFile(
            spdxId = "${SpdxConstants.REF_PREFIX}File-${nextFileIndex.getAndIncrement()}",
            checksums = listOf(
                SpdxChecksum(algorithm = SpdxChecksum.Algorithm.SHA1, checksumValue = fileFindings.sha1)
            ),
            filename = fileFindings.path,
            licenseConcluded = SpdxConstants.NOASSERTION,
            licenseInfoInFiles = fileFindings.licenses.map { it.toString() }.ifEmpty { listOf(SpdxConstants.NONE) },
            copyrightText = fileFindings.copyrights.sorted().joinToString("\n").ifBlank { SpdxConstants.NONE }
        ).also { spdxFile ->
            runCatching {
                spdxFile.validate()
            }.onFailure {
                logger.error { "Validation failed for '${spdxFile.spdxId}': ${it.message}" }
            }
        }
    }

/**
 * This class holds license and copyright findings as well as the sha1 checksum corresponding to a file located under
 * [path].
 */
private data class FileFindings(
    val path: String,
    val sha1: String,
    val licenses: Set<SpdxExpression>,
    val copyrights: Set<String>
)

/**
 * Return the [FileFindings] for the package denoted by [id]. The result contains exactly one entry for each file which
 * has at least one finding and no entry for files without any findings.
 */
private fun OrtResult.getFileFindings(
    id: Identifier,
    licenseInfoResolver: LicenseInfoResolver,
    sourceCodeOrigin: SourceCodeOrigin
): Set<FileFindings>? {
    val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(id)

    val licensesByFilePath = resolvedLicenseInfo.getLicensesByFilePath(sourceCodeOrigin) ?: return null
    val copyrightsByFilePath = resolvedLicenseInfo.getCopyrightsByFilePath(sourceCodeOrigin) ?: return null
    val sha1ByFilepath = getFileListForId(id)?.takeIf { it.provenance.matches(sourceCodeOrigin) }?.files?.associateBy(
        { it.path }, { it.sha1 }
    ) ?: return null

    val findingPaths = copyrightsByFilePath.keys + licensesByFilePath.keys

    return findingPaths.mapNotNullTo(mutableSetOf()) { path ->
        FileFindings(
            path = path,
            // The sha1 is not available in case path is not within the VCS path.
            sha1 = sha1ByFilepath[path] ?: return@mapNotNullTo null,
            licenses = licensesByFilePath[path].orEmpty(),
            copyrights = copyrightsByFilePath[path].orEmpty()
        )
    }
}

/**
 * Return the file paths corresponding to all non-excluded, curated copyright statements for the specified
 * [sourceCodeOrigin] associated with the copyright findings under the respective path, or `null` if the
 * [sourceCodeOrigin] hasn't been scanned.
 */
private fun ResolvedLicenseInfo.getCopyrightsByFilePath(sourceCodeOrigin: SourceCodeOrigin): Map<String, Set<String>>? {
    if (licenseInfo.detectedLicenseInfo.findings.none { it.provenance.matches(sourceCodeOrigin) }) return null

    val result = mutableMapOf<String, MutableSet<String>>()

    licenses.forEach { resolvedLicense ->
        resolvedLicense.locations.forEach { location ->
            if (location.provenance.matches(sourceCodeOrigin) && location.matchingPathExcludes.isEmpty()) {
                location.copyrights.forEach { copyrightFinding ->
                    if (copyrightFinding.matchingPathExcludes.isEmpty()) {
                        result.getOrPut(copyrightFinding.location.path) { mutableSetOf() } += copyrightFinding.statement
                    }
                }
            }
        }
    }

    unmatchedCopyrights.forEach { (provenance, copyrightFindings) ->
        if (provenance.matches(sourceCodeOrigin)) {
            copyrightFindings.forEach { copyrightFinding ->
                if (copyrightFinding.matchingPathExcludes.isEmpty()) {
                    result.getOrPut(copyrightFinding.location.path) { mutableSetOf() } += copyrightFinding.statement
                }
            }
        }
    }

    return result
}

/**
 * Return the file paths corresponding to all non-excluded, curated license findings for the specified
 * [sourceCodeOrigin] associated with the licenses detected under the respective path, or `null` ff the
 * [sourceCodeOrigin] hasn't been scanned.
 */
private fun ResolvedLicenseInfo.getLicensesByFilePath(
    sourceCodeOrigin: SourceCodeOrigin
): Map<String, Set<SpdxExpression>>? {
    // TODO: Consider refactoring ResolvedLicenseInfo, so that it is possible to obtain the information needed
    //       without relying on findings, which in turn requires re-applying curations and filtering by path excludes.
    val findingsForSourceCodeOrigin = licenseInfo.detectedLicenseInfo.findings.filter {
        it.provenance.matches(sourceCodeOrigin)
    }.takeUnless { it.isEmpty() } ?: return null

    return findingsForSourceCodeOrigin
        .flatMap { it.getLicensesByFilePath().toList() }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.flatten().toSet() }
}

/**
 * Return the file paths corresponding to all non-excluded, curated license findings associated with the licenses
 * detected under the respective path.
 */
private fun Findings.getLicensesByFilePath(): Map<String, Set<SpdxExpression>> {
    fun LicenseFinding.path() = location.prependedPath(relativeFindingsPath)

    return licenses
        .filter { licenseFinding -> pathExcludes.none { it.matches(licenseFinding.path()) } }
        .let { licenseFindings -> FindingCurationMatcher().applyAll(licenseFindings, licenseFindingCurations) }
        .mapNotNull { curationResult -> curationResult.curatedFinding }
        .groupBy({ licenseFinding -> licenseFinding.path() }, { licenseFinding -> licenseFinding.license })
        .mapValues { (_, licenses) -> licenses.toSet() }
}

/**
 * Return true if and only if this provenance is of the type specified by [sourceCodeOrigin].
 */
private fun Provenance.matches(sourceCodeOrigin: SourceCodeOrigin): Boolean =
    when (sourceCodeOrigin) {
        SourceCodeOrigin.VCS -> this is RepositoryProvenance
        SourceCodeOrigin.ARTIFACT -> this is ArtifactProvenance
    }

internal val SpdxDocumentReporterConfig.wantSpdx23: Boolean
    get() = when (spdxVersion) {
        SPDX_VERSION_2_2 -> false
        SPDX_VERSION_2_3 -> true
        else -> throw IllegalArgumentException("Unsupported SPDX version '$spdxVersion'.")
    }
