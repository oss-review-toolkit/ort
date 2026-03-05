/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.toPackageUrl
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.PACKAGE_TYPE_SPDX
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxExternalReference
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxPackage
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxRelationship

private val SPDX_VCS_PREFIXES = mapOf(
    "git+" to VcsType.GIT,
    "hg+" to VcsType.MERCURIAL,
    "bzr+" to VcsType.UNKNOWN,
    "svn+" to VcsType.SUBVERSION
)

/**
 * Return the [SpdxPackage] in the [SpdxDocument] that denotes a project, or null if no project but only packages are
 * defined.
 */
internal fun SpdxDocument.projectPackage(): SpdxPackage? =
    // An SpdxDocument that describes a project must have at least 2 packages, one for the project itself, and another
    // one for at least one dependency package.
    packages.takeIf { it.size > 1 || (it.size == 1 && externalDocumentRefs.isNotEmpty()) }
        // The package that describes a project must have an "empty" package filename (as the "filename" is the project
        // directory itself).
        ?.singleOrNull { it.packageFilename.isEmpty() || it.packageFilename == "." }

/**
 * Try to find an [SpdxExternalReference] in this [SpdxPackage] of type purl from which the scope of a
 * package manager dependency can be extracted. Return this scope or *null* if cannot be determined.
 */
internal fun SpdxPackage.extractScopeFromExternalReferences(): String? =
    externalRefs.filter { it.referenceType == SpdxExternalReference.Type.Purl }
        .firstNotNullOfOrNull { it.referenceLocator.toPackageUrl()?.qualifiers?.get("scope") }

/**
 * Return the concluded license to be used in ORT's data model, which uses null instead of NOASSERTION.
 */
internal fun SpdxPackage.getConcludedLicense(): SpdxExpression? =
    licenseConcluded.takeUnless { it == SpdxConstants.NOASSERTION }?.toSpdx()

/**
 * Return the declared license to be used in ORT's data model, which expects a not present value to be an empty set
 * instead of NONE or NOASSERTION.
 */
internal fun SpdxPackage.getDeclaredLicense(): Set<String> =
    setOfNotNull(licenseDeclared.takeIf { SpdxConstants.isPresent(it) })

/**
 * Return a [RemoteArtifact] for the artifact that the [downloadLocation][SpdxPackage.downloadLocation] points to. If
 * the download location is a "not present" value, or if it points to a VCS location instead, return null.
 */
internal fun SpdxPackage.getRemoteArtifact(): RemoteArtifact? =
    when {
        SpdxConstants.isNotPresent(downloadLocation) -> null

        SPDX_VCS_PREFIXES.any { (prefix, _) -> downloadLocation.startsWith(prefix) } -> null

        else -> {
            if (downloadLocation.endsWith(".git")) {
                logger.warn {
                    "The download location $downloadLocation of SPDX package '$spdxId' looks like a Git repository " +
                        "URL but it lacks the 'git+' prefix and thus will be treated as an artifact URL."
                }
            }

            RemoteArtifact(downloadLocation, Hash.NONE)
        }
    }

/**
 * Return the [VcsInfo] contained in the [downloadLocation][SpdxPackage.downloadLocation], or null if the download
 * location is a "not present" value / does not point to a VCS location.
 */
internal fun SpdxPackage.getVcsInfo(): VcsInfo? {
    if (SpdxConstants.isNotPresent(downloadLocation)) return null

    return SPDX_VCS_PREFIXES.mapNotNull { (prefix, vcsType) ->
        downloadLocation.withoutPrefix(prefix)?.let { url ->
            var vcsUrl = url

            val vcsPath = vcsUrl.substringAfterLast('#', "")
            vcsUrl = vcsUrl.removeSuffix("#$vcsPath")

            val vcsRevision = vcsUrl.substringAfterLast('@', "")
            vcsUrl = vcsUrl.removeSuffix("@$vcsRevision")

            VcsInfo(vcsType, vcsUrl, vcsRevision, path = vcsPath)
        }
    }.firstOrNull()
}

/**
 * Return the location of the first [external reference][SpdxExternalReference] of the given [type] in this
 * [SpdxPackage], or null if there is no such reference.
 */
internal fun SpdxPackage.locateExternalReference(type: SpdxExternalReference.Type): String? =
    externalRefs.find { it.referenceType == type }?.referenceLocator

/**
 * Return a CPE identifier for this package if present. Search for all CPE versions.
 */
internal fun SpdxPackage.locateCpe(): String? =
    locateExternalReference(SpdxExternalReference.Type.Cpe23Type)
        ?: locateExternalReference(SpdxExternalReference.Type.Cpe22Type)

/**
 * Create an [Identifier] out of this [SpdxPackage].
 */
internal fun SpdxPackage.toIdentifier(type: String) =
    Identifier(
        type = type,
        namespace = listOfNotNull(supplier, originator).firstOrNull()
            ?.withoutPrefix(SpdxConstants.ORGANIZATION).orEmpty().sanitize(),
        name = name.sanitize(),
        version = versionInfo.sanitize()
    )

/**
 * Create a [Package] out of this [SpdxPackage].
 */
internal fun SpdxPackage.toPackage(definitionFile: File?, doc: SpdxResolvedDocument): Package {
    val packageDescription = description.ifEmpty { summary }

    // If the VCS information cannot be determined from the VCS working tree itself, fall back to try getting it
    // from the download location.
    val packageDir = definitionFile?.resolveSibling(packageFilename)
    val vcs = packageDir?.let { VersionControlSystem.forDirectory(it)?.getInfo() } ?: getVcsInfo().orEmpty()

    val generatedFromRelations = doc.relationships.filter {
        it.relationshipType == SpdxRelationship.Type.GENERATED_FROM
    }

    val isBinaryArtifact = generatedFromRelations.any { it.spdxElementId == spdxId }
        && generatedFromRelations.none { it.relatedSpdxElement == spdxId }

    val id = toIdentifier(PACKAGE_TYPE_SPDX)
    val artifact = getRemoteArtifact()

    val purl = locateExternalReference(SpdxExternalReference.Type.Purl)
        ?: artifact
            ?.let { if (it.hash.algorithm in HashAlgorithm.VERIFIABLE) it else it.copy(hash = Hash.NONE) }
            ?.let { id.toPurl(ArtifactProvenance(it)) }
        ?: id.toPurl()

    return Package(
        id = id,
        purl = purl,
        cpe = locateCpe(),
        authors = originator.wrapPresentInSet(),
        declaredLicenses = getDeclaredLicense(),
        concludedLicense = getConcludedLicense(),
        description = packageDescription,
        homepageUrl = homepage.mapNotPresentToEmpty(),
        binaryArtifact = artifact.takeIf { isBinaryArtifact }.orEmpty(),
        sourceArtifact = artifact.takeUnless { isBinaryArtifact }.orEmpty(),
        vcs = vcs
    )
}
