/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import java.io.File
import java.net.URI
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.createOrtTempDir
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxModelMapper
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdx.model.SpdxExternalDocumentReference
import org.ossreviewtoolkit.utils.spdx.model.SpdxExternalReference
import org.ossreviewtoolkit.utils.spdx.model.SpdxPackage
import org.ossreviewtoolkit.utils.spdx.model.SpdxRelationship
import org.ossreviewtoolkit.utils.spdx.toSpdx

private const val MANAGER_NAME = "SpdxDocumentFile"
private const val DEFAULT_SCOPE_NAME = "default"

private val SPDX_LINKAGE_RELATIONSHIPS = mapOf(
    SpdxRelationship.Type.DYNAMIC_LINK to PackageLinkage.DYNAMIC,
    SpdxRelationship.Type.STATIC_LINK to PackageLinkage.STATIC
)

private val SPDX_SCOPE_RELATIONSHIPS = listOf(
    SpdxRelationship.Type.BUILD_DEPENDENCY_OF,
    SpdxRelationship.Type.DEV_DEPENDENCY_OF,
    SpdxRelationship.Type.OPTIONAL_DEPENDENCY_OF,
    SpdxRelationship.Type.PROVIDED_DEPENDENCY_OF,
    SpdxRelationship.Type.RUNTIME_DEPENDENCY_OF,
    SpdxRelationship.Type.TEST_DEPENDENCY_OF
)

private val SPDX_VCS_PREFIXES = mapOf(
    "git+" to VcsType.GIT,
    "hg+" to VcsType.MERCURIAL,
    "bzr+" to VcsType.UNKNOWN,
    "svn+" to VcsType.SUBVERSION
)

/**
 * Return true if the [SpdxDocument] describes a project. Otherwise, if it describes a package, return false.
 */
private fun SpdxDocument.isProject(): Boolean = projectPackage() != null

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
 * Get the [SpdxPackage] from the [SpdxExternalDocumentReference] that the [packageId] refers to, where [definitionFile]
 * is used to resolve local relative URIs to files.
 */
internal fun SpdxExternalDocumentReference.getSpdxPackage(
    packageId: String,
    definitionFile: File,
    issues: MutableList<OrtIssue>
): SpdxPackage? {
    val externalSpdxDocument = resolve(definitionFile, issues) ?: return null

    if (externalSpdxDocument.isProject()) {
        issues += createAndLogIssue(
            source = MANAGER_NAME,
            message = "$externalDocumentId refers to a file that contains more than a single package. This is " +
                    "currently not supported."
        )
        return null
    }

    val spdxPackage = externalSpdxDocument.packages.find { it.spdxId == packageId } ?: run {
        issues += createAndLogIssue(
            source = MANAGER_NAME,
            message = "$packageId can not be found in external document $externalDocumentId."
        )
        return null
    }

    val spdxDocumentPath = URI(spdxDocument).path
    return spdxPackage.copy(packageFilename = definitionFile.resolveSibling(spdxDocumentPath).parentFile.absolutePath)
}

/**
 * Return the [SpdxDocument] this [SpdxExternalDocumentReference]'s [SpdxDocument] refers to.
 */
private fun SpdxExternalDocumentReference.resolve(definitionFile: File, issues: MutableList<OrtIssue>): SpdxDocument? {
    val uri = runCatching { URI(spdxDocument) }.getOrElse {
        issues += createAndLogIssue(
            source = MANAGER_NAME,
            message = "'$spdxDocument' identified by $externalDocumentId is not a valid URI. }"
        )
        return null
    }

    var tempDir: File? = null

    val spdxFile = if (uri.scheme.equals("file", ignoreCase = true) || !uri.isAbsolute) {
        val referencedFile = definitionFile.resolveSibling(uri.path).absoluteFile.normalize()
        referencedFile.takeIf { it.isFile } ?: run {
            issues += createAndLogIssue(
                source = MANAGER_NAME,
                message = "The file '$referencedFile' pointed to by URI '$uri' does not exist."
            )
            return null
        }
    } else {
        tempDir = createOrtTempDir()
        OkHttpClientHelper.downloadFile(uri.toString(), tempDir).getOrNull() ?: run {
            tempDir.safeDeleteRecursively(force = true)
            issues += createAndLogIssue(
                source = MANAGER_NAME,
                message = "Failed to download '$spdxDocument' from $uri."
            )
            return null
        }
    }

    val hash = Hash.create(checksum.checksumValue, checksum.algorithm.name)
    if (!hash.verify(spdxFile)) {
        val referencedFile = tempDir?.let { uri } ?: spdxFile
        issues += createAndLogIssue(
            source = MANAGER_NAME,
            message = "The file '$referencedFile' referred from '$definitionFile' does not match the expected $hash."
        )
    }

    return SpdxModelMapper.read<SpdxDocument>(spdxFile).also { tempDir?.safeDeleteRecursively(force = true) }
}

/**
 * Return the concluded license to be used in ORT's data model, which expects a not present value to be null instead
 * of NONE or NOASSERTION.
 */
private fun SpdxPackage.getConcludedLicense(): SpdxExpression? =
    licenseConcluded.takeIf { SpdxConstants.isPresent(it) }?.toSpdx()

/**
 * Return a [RemoteArtifact] for the binary artifact that the [downloadLocation][SpdxPackage.downloadLocation] points
 * to. If the download location is a "not present" value, or if it points to a source artifact or a VCS location
 * instead, return null.
 */
internal fun SpdxPackage.getBinaryArtifact(): RemoteArtifact? =
    getRemoteArtifact().takeUnless {
        // Note: The "FilesAnalyzed" field "Indicates whether the file content of this package has been available for or
        // subjected to analysis when creating the SPDX document". It does not indicate whether files were actually
        // analyzed.
        // If files were available, do *not* consider this do be a *binary* artifact.
        filesAnalyzed
    }

/**
 * Return a [RemoteArtifact] for the source artifact that the [downloadLocation][SpdxPackage.downloadLocation] points
 * to. If the download location is a "not present" value, or if it points to a binary artifact or a VCS location
 * instead, return null.
 */
internal fun SpdxPackage.getSourceArtifact(): RemoteArtifact? =
    getRemoteArtifact().takeIf {
        // Note: The "FilesAnalyzed" field "Indicates whether the file content of this package has been available for or
        // subjected to analysis when creating the SPDX document". It does not indicate whether files were actually
        // analyzed.
        // If files were available, *do* consider this do be a *source* artifact.
        filesAnalyzed
    }

private fun SpdxPackage.getRemoteArtifact(): RemoteArtifact? =
    when {
        SpdxConstants.isNotPresent(downloadLocation) -> null
        SPDX_VCS_PREFIXES.any { (prefix, _) -> downloadLocation.startsWith(prefix) } -> null
        else -> RemoteArtifact(downloadLocation, Hash.NONE)
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
private fun SpdxPackage.locateExternalReference(type: SpdxExternalReference.Type): String? =
    externalRefs.find { it.referenceType == type }?.referenceLocator

/**
 * Return whether the string has the format of an [SpdxExternalDocumentReference], with or without an additional
 * package id.
 */
private fun String.isExternalDocumentReferenceId(): Boolean = startsWith(SpdxConstants.DOCUMENT_REF_PREFIX)

/**
 * Map a "not preset" SPDX value, i.e. NONE or NOASSERTION, to an empty string.
 */
private fun String.mapNotPresentToEmpty(): String = takeUnless { SpdxConstants.isNotPresent(it) }.orEmpty()

/**
 * Wrap any "present" SPDX value in a sorted set, or return an empty sorted set otherwise.
 */
private fun String?.wrapPresentInSortedSet(): SortedSet<String> {
    if (SpdxConstants.isPresent(this)) {
        withoutPrefix(SpdxConstants.PERSON)?.let { persons ->
            // In case of a person, allow a comma-separated list of persons.
            return persons.split(',').mapTo(sortedSetOf()) { it.trim() }
        }

        // Do not split an organization like "Acme, Inc." by comma.
        withoutPrefix(SpdxConstants.ORGANIZATION)?.let {
            return sortedSetOf(it)
        }
    }

    return sortedSetOf()
}

/**
 * Return the [PackageLinkage] between [dependency] and [dependant] as specified in [relationships]. If no
 * relationship is found, return [PackageLinkage.DYNAMIC].
 */
private fun getLinkageForDependency(
    dependency: SpdxPackage,
    dependant: String,
    relationships: List<SpdxRelationship>
): PackageLinkage =
    relationships.mapNotNull { relation ->
        SPDX_LINKAGE_RELATIONSHIPS[relation.relationshipType]?.takeIf {
            val relationId = if (relation.relatedSpdxElement.isExternalDocumentReferenceId()) {
                relation.relatedSpdxElement.substringAfter(":")
            } else {
                relation.relatedSpdxElement
            }

            relationId == dependency.spdxId && relation.spdxElementId == dependant
        }
    }.takeUnless { it.isEmpty() }?.single() ?: PackageLinkage.DYNAMIC

/**
 * Return true if the [relation] as defined in [relationships] describes that the [source] depends on the [target].
 */
private fun hasDependsOnRelationship(
    source: String, target: String, relation: SpdxRelationship.Type, relationships: List<SpdxRelationship>
): Boolean {
    if (relation == SpdxRelationship.Type.DEPENDS_ON) return true

    val hasScopeRelationship = relationships.any {
        it.relationshipType in SPDX_SCOPE_RELATIONSHIPS
                // Scope relationships are defined in "reverse" as a "dependency of".
                && it.relatedSpdxElement == source && it.spdxElementId == target
    }

    return relation in SPDX_LINKAGE_RELATIONSHIPS && !hasScopeRelationship
}

/**
 * A "fake" package manager implementation that uses SPDX documents as definition files to declare projects and describe
 * packages. See https://github.com/spdx/spdx-spec/issues/439 for details.
 */
class SpdxDocumentFile(
    managerName: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(managerName, analysisRoot, analyzerConfig, repoConfig) {
    private val spdxDocumentForFile = mutableMapOf<File, SpdxDocument>()
    private val packageForExternalDocumentId = mutableMapOf<String, SpdxPackage>()

    class Factory : AbstractPackageManagerFactory<SpdxDocumentFile>(MANAGER_NAME) {
        override val globsForDefinitionFiles = listOf("*.spdx.yml", "*.spdx.yaml", "*.spdx.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = SpdxDocumentFile(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    /**
     * Create an [Identifier] out of this [SpdxPackage].
     */
    private fun SpdxPackage.toIdentifier() =
        Identifier(
            type = managerName,
            namespace = listOfNotNull(supplier, originator).firstOrNull()
                ?.withoutPrefix(SpdxConstants.ORGANIZATION).orEmpty(),
            name = name,
            version = versionInfo
        )

    /**
     * Create a [Package] out of this [SpdxPackage].
     */
    private fun SpdxPackage.toPackage(definitionFile: File): Package {
        val packageDescription = description.takeUnless { it.isEmpty() } ?: summary

        // If the VCS information cannot be determined from the VCS working tree itself, fall back to try getting it
        // from the download location.
        val packageDir = definitionFile.resolveSibling(packageFilename)
        val vcs = VersionControlSystem.forDirectory(packageDir)?.getInfo() ?: getVcsInfo().orEmpty()

        val id = toIdentifier()

        return Package(
            id = id,
            purl = locateExternalReference(SpdxExternalReference.Type.Purl) ?: id.toPurl(),
            authors = originator.wrapPresentInSortedSet(),
            declaredLicenses = sortedSetOf(licenseDeclared),
            concludedLicense = getConcludedLicense(),
            description = packageDescription,
            homepageUrl = homepage.mapNotPresentToEmpty(),
            binaryArtifact = getBinaryArtifact().orEmpty(),
            sourceArtifact = getSourceArtifact().orEmpty(),
            vcs = vcs
        )
    }

    /**
     * Get the [SpdxPackage] for the given [identifier] by resolving against packages or external document references
     * contained in [doc].
     */
    private fun getSpdxPackageForId(
        doc: SpdxDocument,
        identifier: String,
        definitionFile: File,
        issues: MutableList<OrtIssue>
    ): SpdxPackage? {
        doc.packages.find { it.spdxId == identifier }?.let { return it }

        val documentRef = identifier.substringBefore(":")
        val externalDocumentReference = doc.externalDocumentRefs.find {
            it.externalDocumentId == documentRef
        } ?: run {
            issues += createAndLogIssue(
                source = MANAGER_NAME,
                message = "ID '$identifier' could neither be resolved to a package nor to an externalDocumentRef."
            )
            return null
        }

        return packageForExternalDocumentId.getOrPut(externalDocumentReference.externalDocumentId) {
            externalDocumentReference.getSpdxPackage(identifier.substringAfter(":"), definitionFile, issues)
                ?: return null
        }
    }

    /**
     * Return the dependencies of [pkg] defined in [doc] of the [SpdxRelationship.Type.DEPENDENCY_OF] type. Identified
     * dependencies are mapped to ORT [Package]s by taking the [definitionFile] into account for the [VcsInfo], and then
     * added to [packages].
     */
    private fun getDependencies(
        pkg: SpdxPackage,
        doc: SpdxDocument,
        definitionFile: File,
        packages: MutableSet<Package>
    ): SortedSet<PackageReference> =
        getDependencies(pkg, doc, definitionFile, packages, SpdxRelationship.Type.DEPENDENCY_OF) { target ->
            val issues = mutableListOf<OrtIssue>()
            getSpdxPackageForId(doc, target, definitionFile, issues)?.let { dependency ->
                packages += dependency.toPackage(definitionFile)

                PackageReference(
                    id = dependency.toIdentifier(),
                    dependencies = getDependencies(dependency, doc, definitionFile, packages),
                    linkage = getLinkageForDependency(dependency, pkg.spdxId, doc.relationships),
                    issues = issues
                )
            }
        }

    /**
     * Return the dependencies of [pkg] defined in [doc] of the given [dependencyOfRelation] type. Optionally, the
     * [SpdxRelationship.Type.DEPENDS_ON] type is handled by [dependsOnCase]. Identified dependencies are mapped to
     * ORT [Package]s by taking the [definitionFile] into account for the [VcsInfo], and then added to [packages].
     */
    private fun getDependencies(
        pkg: SpdxPackage,
        doc: SpdxDocument,
        definitionFile: File,
        packages: MutableSet<Package>,
        dependencyOfRelation: SpdxRelationship.Type,
        dependsOnCase: (String) -> PackageReference? = { null }
    ): SortedSet<PackageReference> =
        doc.relationships.mapNotNullTo(sortedSetOf()) { (source, target, relation) ->
            val issues = mutableListOf<OrtIssue>()

            when {
                // Dependencies can either be defined on the target...
                pkg.spdxId.equals(target, ignoreCase = true) && relation == dependencyOfRelation -> {
                    if (pkg.spdxId != target) {
                        issues += createAndLogIssue(
                            source = managerName,
                            message = "Source '${pkg.spdxId}' has to match target '$target' case-sensitively."
                        )
                    }

                    getSpdxPackageForId(doc, source, definitionFile, issues)?.let { dependency ->
                        packages += dependency.toPackage(definitionFile)
                        PackageReference(
                            id = dependency.toIdentifier(),
                            dependencies = getDependencies(
                                dependency,
                                doc,
                                definitionFile,
                                packages,
                                SpdxRelationship.Type.DEPENDENCY_OF,
                                dependsOnCase
                            ),
                            issues = issues,
                            linkage = getLinkageForDependency(dependency, target, doc.relationships)
                        )
                    }
                }

                // ...or on the source.
                pkg.spdxId.equals(source, ignoreCase = true)
                        && hasDependsOnRelationship(source, target, relation, doc.relationships) -> {
                    if (pkg.spdxId != source) {
                        issues += createAndLogIssue(
                            source = managerName,
                            message = "Source '$source' has to match target '${pkg.spdxId}' case-sensitively."
                        )
                    }

                    val pkgRef = dependsOnCase(target)
                    pkgRef?.copy(issues = issues + pkgRef.issues)
                }

                else -> null
            }
        }

    /**
     * Return a [Scope] created from the given type of [relation] for [projectPackage] in [spdxDocument], or `null` if
     * there are no such relations. Identified dependencies are mapped to ORT [Package]s by taking the [definitionFile]
     * into account for the [VcsInfo], and then added to [packages].
     */
    private fun createScope(
        spdxDocument: SpdxDocument,
        projectPackage: SpdxPackage,
        relation: SpdxRelationship.Type,
        definitionFile: File,
        packages: MutableSet<Package>
    ): Scope? =
        getDependencies(projectPackage, spdxDocument, definitionFile, packages, relation).takeUnless {
            it.isEmpty()
        }?.let {
            Scope(
                name = relation.name.removeSuffix("_DEPENDENCY_OF").lowercase(),
                dependencies = it
            )
        }

    override fun mapDefinitionFiles(definitionFiles: List<File>): List<File> =
        definitionFiles.associateWith {
            SpdxModelMapper.read<SpdxDocument>(it)
        }.filterTo(spdxDocumentForFile) { (_, spdxDocument) ->
            // Distinguish whether we have a project-style SPDX document that describes a project and its dependencies,
            // or a package-style SPDX document that describes a single (dependency-)package.
            spdxDocument.isProject()
        }.keys.toList().also { remainingFiles ->
            if (remainingFiles.isEmpty()) return definitionFiles

            val discardedFiles = definitionFiles - remainingFiles
            if (discardedFiles.isNotEmpty()) {
                log.info {
                    "Discarded the following non-project SPDX files: ${discardedFiles.joinToString { "'$it'" }}"
                }
            }
        }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        // For direct callers of this function mapDefinitionFiles() did not populate the map before, so add a fallback.
        val spdxDocument = spdxDocumentForFile.getOrPut(definitionFile) { SpdxModelMapper.read(definitionFile) }

        val packages = mutableSetOf<Package>()
        val scopes = sortedSetOf<Scope>()

        val projectPackage = if (!spdxDocument.isProject()) {
            spdxDocument.packages[0]
        } else {
            requireNotNull(spdxDocument.projectPackage()) {
                "The SPDX document file at '$definitionFile' does not describe a project."
            }
        }

        log.info {
            "File '$definitionFile' contains SPDX document '${spdxDocument.name}' which describes project " +
                    "'${projectPackage.name}'."
        }

        scopes += SPDX_SCOPE_RELATIONSHIPS.mapNotNullTo(sortedSetOf()) { type ->
            createScope(spdxDocument, projectPackage, type, definitionFile, packages)
        }

        scopes += Scope(
            name = DEFAULT_SCOPE_NAME,
            dependencies = getDependencies(projectPackage, spdxDocument, definitionFile, packages)
        )

        val project = Project(
            id = projectPackage.toIdentifier(),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = projectPackage.originator.wrapPresentInSortedSet(),
            declaredLicenses = sortedSetOf(projectPackage.licenseDeclared),
            vcs = processProjectVcs(definitionFile.parentFile, VcsInfo.EMPTY),
            homepageUrl = projectPackage.homepage.mapNotPresentToEmpty(),
            scopeDependencies = scopes
        )

        return listOf(ProjectAnalyzerResult(project, packages.toSortedSet()))
    }
}
