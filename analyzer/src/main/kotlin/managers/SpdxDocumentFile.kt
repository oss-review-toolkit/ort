/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import java.io.File
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
    }.singleOrNull() ?: PackageLinkage.DYNAMIC

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
            val dependency = doc.packages.singleOrNull { it.spdxId == target }
                ?: throw IllegalArgumentException("No single package with target ID '$target' found.")

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

                    val dependency = doc.packages.singleOrNull { it.spdxId == source }
                        ?: throw IllegalArgumentException("No single package with source ID '$source' found.")

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

    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val spdxDocument = SpdxModelMapper.read<SpdxDocument>(definitionFile)

        // Distinguish whether we have a project-style SPDX document that describes a project and its dependencies, or a
        // package-style SPDX document that describes a single (dependency-)package.
        val projectPackage = spdxDocument.packages.singleOrNull { it.packageFilename.isEmpty() }

        val packages = mutableSetOf<Package>()

        val project = if (projectPackage != null) {
            val scopes = SPDX_SCOPE_RELATIONSHIPS.mapNotNullTo(sortedSetOf()) { type ->
                createScope(spdxDocument, projectPackage, type, workingDir, packages)
            }

            scopes += Scope(
                name = DEFAULT_SCOPE_NAME,
                dependencies = getDependencies(projectPackage, spdxDocument, workingDir, packages)
            )

            Project(
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
        } else {
            // TODO: Add support for "package.spdx.yml" files. How to deal with relationships between SPDX packages if
            //       we do not have a project to attach dependencies to?
            Project.EMPTY.copy(id = Identifier.EMPTY.copy(type = managerName))
        }

        return listOf(ProjectAnalyzerResult(project, packages.toSortedSet()))
    }
}
