/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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
import org.ossreviewtoolkit.spdx.SpdxConstants
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.SpdxModelMapper
import org.ossreviewtoolkit.spdx.model.SpdxDocument
import org.ossreviewtoolkit.spdx.model.SpdxExternalDocumentReference
import org.ossreviewtoolkit.spdx.model.SpdxPackage
import org.ossreviewtoolkit.spdx.model.SpdxRelationship
import org.ossreviewtoolkit.spdx.toSpdx
import org.ossreviewtoolkit.utils.withoutPrefix

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
    packages.takeIf { it.size > 1 || (it.size == 1 && externalDocumentRefs.isNotEmpty()) }
        ?.find { it.packageFilename.isEmpty() || it.packageFilename == "." }

/**
 * Return the organization from an "originator", "supplier", or "annotator" string, or null if no organization is
 * specified.
 */
private fun String.extractOrganization(): String? =
    lineSequence().mapNotNull { line ->
        line.withoutPrefix(SpdxConstants.ORGANIZATION)
    }.firstOrNull()

/**
 * Map a "not preset" SPDX value, i.e. NONE or NOASSERTION, to an empty string.
 */
private fun String.mapNotPresentToEmpty(): String = takeUnless { SpdxConstants.isNotPresent(it) }.orEmpty()

/**
 * Get the [SpdxPackage] for the given [externalDocumentReference], where [workingDir] is used to resolve local
 * relative URIs to files.
 */
private fun getSpdxPackageForDocumentRef(
    externalDocumentReference: SpdxExternalDocumentReference,
    workingDir: File
): SpdxPackage {
    val externalSpdxDocument = resolveExternalDocumentReference(externalDocumentReference, workingDir)
    val spdxDocument = SpdxModelMapper.read<SpdxDocument>(externalSpdxDocument)

    if (spdxDocument.isProject()) {
        throw IllegalArgumentException("${externalDocumentReference.externalDocumentId} refers to a file that " +
                "contains more than a single package. This is currently not supported yet.")
    }

    val spdxPackage = spdxDocument.packages.single()
    return spdxPackage.copy(packageFilename = externalSpdxDocument.parentFile.absolutePath)
}

/**
 * Return [File] referred to by [SpdxExternalDocumentReference.spdxDocument].
 */
private fun resolveExternalDocumentReference(
    externalDocumentReference: SpdxExternalDocumentReference,
    workingDir: File
): File {
    val uri = runCatching { URI(externalDocumentReference.spdxDocument) }.getOrElse {
        throw IllegalArgumentException("'${externalDocumentReference.spdxDocument}' identified by " +
                "${externalDocumentReference.externalDocumentId} is not a valid URI. }")
    }

    if (uri.scheme.equals("file", ignoreCase = true) || !uri.isAbsolute) {
        val referencedFile = workingDir.resolve(uri.path)
        return referencedFile.takeIf { it.isFile }
            ?: throw IllegalArgumentException("The local file URI '$uri' does not point to an existing file. ")
    }

    throw IllegalArgumentException("Only URIs to local files are supported for now, but '$uri' is none.")
}

/**
 * Return the concluded license to be used in ORT's data model, which expects a not present value to be null instead
 * of NONE or NOASSERTION.
 */
private fun getConcludedLicense(pkg: SpdxPackage): SpdxExpression? =
    pkg.licenseConcluded.takeIf { SpdxConstants.isPresent(it) }?.toSpdx()

/**
 * Return a [RemoteArtifact] for the binary artifact that the [pkg]'s [downloadLocation][SpdxPackage.downloadLocation]
 * points to. If the download location is a "not present" value, or if it points to a source artifact or a VCS location
 * instead, return null.
 */
internal fun getBinaryArtifact(pkg: SpdxPackage): RemoteArtifact? =
    getRemoteArtifact(pkg).takeUnless {
        // Note: The "FilesAnalyzed" field "Indicates whether the file content of this package has been available for or
        // subjected to analysis when creating the SPDX document". It does not indicate whether files were actually
        // analyzed.
        // If files were available, do *not* consider this do be a *binary* artifact.
        pkg.filesAnalyzed
    }

/**
 * Return a [RemoteArtifact] for the source artifact that the [pkg]'s [downloadLocation][SpdxPackage.downloadLocation]
 * points to. If the download location is a "not present" value, or if it points to a binary artifact or a VCS location
 * instead, return null.
 */
internal fun getSourceArtifact(pkg: SpdxPackage): RemoteArtifact? =
    getRemoteArtifact(pkg).takeIf {
        // Note: The "FilesAnalyzed" field "Indicates whether the file content of this package has been available for or
        // subjected to analysis when creating the SPDX document". It does not indicate whether files were actually
        // analyzed.
        // If files were available, *do* consider this do be a *source* artifact.
        pkg.filesAnalyzed
    }

private fun getRemoteArtifact(pkg: SpdxPackage): RemoteArtifact? =
    when {
        SpdxConstants.isNotPresent(pkg.downloadLocation) -> null
        SPDX_VCS_PREFIXES.any { (prefix, _) -> pkg.downloadLocation.startsWith(prefix) } -> null
        else -> RemoteArtifact(pkg.downloadLocation, Hash.NONE)
    }

/**
 * Return the [VcsInfo] contained in [pkg]'s [downloadLocation][SpdxPackage.downloadLocation], or null if the download
 * location is a "not present" value / does not point to a VCS location.
 */
internal fun getVcsInfo(pkg: SpdxPackage): VcsInfo? {
    if (SpdxConstants.isNotPresent(pkg.downloadLocation)) return null

    return SPDX_VCS_PREFIXES.mapNotNull { (prefix, vcsType) ->
        pkg.downloadLocation.withoutPrefix(prefix)?.let { url ->
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
            relation.relatedSpdxElement == dependency.spdxId && relation.spdxElementId == dependant
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

    class Factory : AbstractPackageManagerFactory<SpdxDocumentFile>("SpdxDocumentFile") {
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
            namespace = originator.extractOrganization().orEmpty(),
            name = name,
            version = versionInfo
        )

    /**
     * Create a [Package] out of this [SpdxPackage].
     */
    private fun SpdxPackage.toPackage(workingDir: File): Package {
        val packageDescription = description.takeUnless { it.isEmpty() } ?: summary

        // If the VCS information cannot be determined from the download location, fall back to try getting it from the
        // VCS working tree itself.
        val vcs = getVcsInfo(this) ?: run {
            val packageDir = workingDir.resolve(packageFilename)
            VersionControlSystem.forDirectory(packageDir)?.getInfo()
        } ?: VcsInfo.EMPTY

        return Package(
            id = toIdentifier(),
            // TODO: Find a way to track authors.
            authors = sortedSetOf(),
            declaredLicenses = sortedSetOf(licenseDeclared),
            concludedLicense = getConcludedLicense(this),
            description = packageDescription,
            homepageUrl = homepage.mapNotPresentToEmpty(),
            binaryArtifact = getBinaryArtifact(this) ?: RemoteArtifact.EMPTY,
            sourceArtifact = getSourceArtifact(this) ?: RemoteArtifact.EMPTY,
            vcs = vcs
        )
    }

    /**
     * Get the [SpdxPackage] for the given [identifier] by resolving against packages an external document references
     * contained in [doc].
     */
    private fun getSpdxPackageForId(doc: SpdxDocument, identifier: String, workingDir: File): SpdxPackage {
        doc.packages.find { it.spdxId == identifier }?.let { return it }

        val documentRef = identifier.substringBefore(":")
        val externalDocumentReference = doc.externalDocumentRefs.find {
            it.externalDocumentId == documentRef
        } ?: throw IllegalArgumentException(
            "No single package or externalDocumentRef with ID '$identifier' found."
        )

        return packageForExternalDocumentId.getOrPut(externalDocumentReference.externalDocumentId) {
            getSpdxPackageForDocumentRef(externalDocumentReference, workingDir)
        }
    }

    /**
     * Return the dependencies of [pkg] defined in [doc] of the [SpdxRelationship.Type.DEPENDENCY_OF] type. Identified
     * dependencies are mapped to ORT [Package]s by taking [workingDir] into account for the [VcsInfo], and then added
     * to [packages].
     */
    private fun getDependencies(
        pkg: SpdxPackage,
        doc: SpdxDocument,
        workingDir: File,
        packages: MutableSet<Package>
    ): SortedSet<PackageReference> =
        getDependencies(pkg, doc, workingDir, packages, SpdxRelationship.Type.DEPENDENCY_OF) { target ->
            val dependency = getSpdxPackageForId(doc, target, workingDir)

            packages += dependency.toPackage(workingDir)

            PackageReference(
                id = dependency.toIdentifier(),
                dependencies = getDependencies(dependency, doc, workingDir, packages),
                linkage = getLinkageForDependency(dependency, pkg.spdxId, doc.relationships)
            )
        }

    /**
     * Return the dependencies of [pkg] defined in [doc] of the given [dependencyOfRelation] type. Optionally, the
     * [SpdxRelationship.Type.DEPENDS_ON] type is handled by [dependsOnCase]. Identified dependencies are mapped to
     * ORT [Package]s by taking [workingDir] into account for the [VcsInfo], and then added to [packages].
     */
    private fun getDependencies(
        pkg: SpdxPackage,
        doc: SpdxDocument,
        workingDir: File,
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

                    val dependency = getSpdxPackageForId(doc, source, workingDir)

                    packages += dependency.toPackage(workingDir)
                    PackageReference(
                        id = dependency.toIdentifier(),
                        dependencies = getDependencies(
                            dependency,
                            doc,
                            workingDir,
                            packages,
                            SpdxRelationship.Type.DEPENDENCY_OF,
                            dependsOnCase
                        ),
                        issues = issues,
                        linkage = getLinkageForDependency(dependency, target, doc.relationships)
                    )
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
     * there are no such relations. Identified dependencies are mapped to ORT [Package]s by taking [workingDir] into
     * account for the [VcsInfo], and then added to [packages].
     */
    private fun createScope(
        spdxDocument: SpdxDocument,
        projectPackage: SpdxPackage,
        relation: SpdxRelationship.Type,
        workingDir: File,
        packages: MutableSet<Package>
    ): Scope? =
        getDependencies(projectPackage, spdxDocument, workingDir, packages, relation).takeUnless { it.isEmpty() }?.let {
            Scope(
                name = relation.name.removeSuffix("_DEPENDENCY_OF").toLowerCase(),
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
        }.keys.toList()

    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        // For direct callers of this function mapDefinitionFiles() did not populate the map before, so add a fallback.
        val spdxDocument = spdxDocumentForFile.getOrPut(definitionFile) { SpdxModelMapper.read(definitionFile) }

        // Guard against direct callers passing SPDX documents that do not describe a project.
        val projectPackage = requireNotNull(spdxDocument.projectPackage()) {
            "The SPDX document file at '$definitionFile' does not describe a project."
        }

        val packages = mutableSetOf<Package>()

        val scopes = SPDX_SCOPE_RELATIONSHIPS.mapNotNullTo(sortedSetOf()) { type ->
            createScope(spdxDocument, projectPackage, type, workingDir, packages)
        }

        scopes += Scope(
            name = DEFAULT_SCOPE_NAME,
            dependencies = getDependencies(projectPackage, spdxDocument, workingDir, packages)
        )

        val project = Project(
            id = projectPackage.toIdentifier(),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            // TODO: Find a way to track authors.
            authors = sortedSetOf(),
            declaredLicenses = sortedSetOf(projectPackage.licenseDeclared),
            vcs = VcsInfo.EMPTY,
            vcsProcessed = processProjectVcs(workingDir, VcsInfo.EMPTY, projectPackage.homepage),
            homepageUrl = projectPackage.homepage.mapNotPresentToEmpty(),
            scopeDependencies = scopes
        )

        return listOf(ProjectAnalyzerResult(project, packages.toSortedSet()))
    }
}
