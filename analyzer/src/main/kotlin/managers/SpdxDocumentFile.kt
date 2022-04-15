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
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.managers.utils.SpdxDocumentCache
import org.ossreviewtoolkit.analyzer.managers.utils.SpdxResolvedDocument
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
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
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
 * Return the concluded license to be used in ORT's data model, which expects a not present value to be null instead
 * of NONE or NOASSERTION.
 */
private fun SpdxPackage.getConcludedLicense(): SpdxExpression? =
    licenseConcluded.takeIf { SpdxConstants.isPresent(it) }?.toSpdx()

/**
 * Return a [RemoteArtifact] for the artifact that the [downloadLocation][SpdxPackage.downloadLocation] points to. If
 * the download location is a "not present" value, or if it points to a VCS location instead, return null.
 */
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
 * Return a CPE identifier for this package if present. Search for all CPE versions.
 */
private fun SpdxPackage.locateCpe(): String? =
    locateExternalReference(SpdxExternalReference.Type.Cpe23Type)
        ?: locateExternalReference(SpdxExternalReference.Type.Cpe22Type)

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
 * Sanitize a string for use as an [Identifier] property where colons are not supported by replacing them with spaces,
 * trimming, and finally collapsing multiple consecutive spaces.
 */
private fun String.sanitize(): String = replace(':', ' ').trim().replace(Regex("\\s{2,}"), " ")

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
 * Return true if the [relation] as defined in [relationships] describes an [SPDX_LINKAGE_RELATIONSHIPS] in the
 * [DEFAULT_SCOPE_NAME] so that the [source] depends on the [target].
 */
private fun hasDefaultScopeLinkage(
    source: String, target: String, relation: SpdxRelationship.Type, relationships: List<SpdxRelationship>
): Boolean {
    if (relation !in SPDX_LINKAGE_RELATIONSHIPS) return false

    val hasScopeRelationship = relationships.any {
        it.relationshipType in SPDX_SCOPE_RELATIONSHIPS
                // Scope relationships are defined in "reverse" as a "dependency of".
                && it.relatedSpdxElement == source && it.spdxElementId == target
    }

    return !hasScopeRelationship
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
    private val spdxDocumentCache = SpdxDocumentCache()

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
                ?.withoutPrefix(SpdxConstants.ORGANIZATION).orEmpty().sanitize(),
            name = name.sanitize(),
            version = versionInfo.sanitize()
        )

    /**
     * Create a [Package] out of this [SpdxPackage].
     */
    private fun SpdxPackage.toPackage(definitionFile: File?, doc: SpdxResolvedDocument): Package {
        val packageDescription = description.takeUnless { it.isEmpty() } ?: summary

        // If the VCS information cannot be determined from the VCS working tree itself, fall back to try getting it
        // from the download location.
        val packageDir = definitionFile?.resolveSibling(packageFilename)
        val vcs = packageDir?.let { VersionControlSystem.forDirectory(it)?.getInfo() } ?: getVcsInfo().orEmpty()

        val generatedFromRelations = doc.relationships.filter {
            it.relationshipType == SpdxRelationship.Type.GENERATED_FROM
        }

        val isBinaryArtifact = generatedFromRelations.any { it.spdxElementId == spdxId }
                && generatedFromRelations.none { it.relatedSpdxElement == spdxId }

        val id = toIdentifier()
        val artifact = getRemoteArtifact()

        return Package(
            id = id,
            purl = locateExternalReference(SpdxExternalReference.Type.Purl) ?: id.toPurl(),
            cpe = locateCpe(),
            authors = originator.wrapPresentInSortedSet(),
            declaredLicenses = sortedSetOf(licenseDeclared),
            concludedLicense = getConcludedLicense(),
            description = packageDescription,
            homepageUrl = homepage.mapNotPresentToEmpty(),
            binaryArtifact = artifact.takeIf { isBinaryArtifact }.orEmpty(),
            sourceArtifact = artifact.takeUnless { isBinaryArtifact }.orEmpty(),
            vcs = vcs
        )
    }

    /**
     * Return the dependencies of the package with the given [pkgId] defined in [doc] of the
     * [SpdxRelationship.Type.DEPENDENCY_OF] type. Identified dependencies are mapped to ORT [Package]s and then
     * added to [packages].
     */
    private fun getDependencies(
        pkgId: String,
        doc: SpdxResolvedDocument,
        packages: MutableSet<Package>
    ): SortedSet<PackageReference> =
        getDependencies(pkgId, doc, packages, SpdxRelationship.Type.DEPENDENCY_OF) { target ->
            val issues = mutableListOf<OrtIssue>()
            doc.getSpdxPackageForId(target, issues)?.let { dependency ->
                packages += dependency.toPackage(doc.getDefinitionFile(target), doc)

                PackageReference(
                    id = dependency.toIdentifier(),
                    dependencies = getDependencies(target, doc, packages),
                    linkage = getLinkageForDependency(dependency, pkgId, doc.relationships),
                    issues = issues
                )
            }
        }

    /**
     * Return the dependencies of the package with the given [pkgId] defined in [doc] of the given
     * [dependencyOfRelation] type. Optionally, the [SpdxRelationship.Type.DEPENDS_ON] type is handled by
     * [dependsOnCase]. Identified dependencies are mapped to ORT [Package]s and then added to [packages].
     */
    private fun getDependencies(
        pkgId: String,
        doc: SpdxResolvedDocument,
        packages: MutableSet<Package>,
        dependencyOfRelation: SpdxRelationship.Type,
        dependsOnCase: (String) -> PackageReference? = { null }
    ): SortedSet<PackageReference> =
        doc.relationships.mapNotNullTo(sortedSetOf()) { (source, relation, target) ->
            val issues = mutableListOf<OrtIssue>()

            val isDependsOnRelation = relation == SpdxRelationship.Type.DEPENDS_ON || hasDefaultScopeLinkage(
                source, target, relation, doc.relationships
            )

            when {
                // Dependencies can either be defined on the target...
                pkgId.equals(target, ignoreCase = true) && relation == dependencyOfRelation -> {
                    if (pkgId != target) {
                        issues += createAndLogIssue(
                            source = managerName,
                            message = "Source '$pkgId' has to match target '$target' case-sensitively."
                        )
                    }

                    doc.getSpdxPackageForId(source, issues)?.let { dependency ->
                        packages += dependency.toPackage(doc.getDefinitionFile(source), doc)
                        PackageReference(
                            id = dependency.toIdentifier(),
                            dependencies = getDependencies(
                                source,
                                doc,
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
                pkgId.equals(source, ignoreCase = true) && isDependsOnRelation -> {
                    if (pkgId != source) {
                        issues += createAndLogIssue(
                            source = managerName,
                            message = "Source '$source' has to match target '$pkgId' case-sensitively."
                        )
                    }

                    val pkgRef = dependsOnCase(target)
                    pkgRef?.copy(issues = issues + pkgRef.issues)
                }

                else -> null
            }
        }

    /**
     * Return a [Scope] created from the given type of [relation] for the [projectPackage][projectPackageId] in
     * [spdxDocument], or `null` if there are no such relations. Identified dependencies are mapped to ORT [Package]s
     * and then added to [packages].
     */
    private fun createScope(
        spdxDocument: SpdxResolvedDocument,
        projectPackageId: String,
        relation: SpdxRelationship.Type,
        packages: MutableSet<Package>
    ): Scope? =
        getDependencies(projectPackageId, spdxDocument, packages, relation).takeUnless {
            it.isEmpty()
        }?.let {
            Scope(
                name = relation.name.removeSuffix("_DEPENDENCY_OF").lowercase(),
                dependencies = it
            )
        }

    override fun mapDefinitionFiles(definitionFiles: List<File>): List<File> =
        definitionFiles.associateWith(spdxDocumentCache::load).filter { (_, spdxDocument) ->
            // Distinguish whether we have a project-style SPDX document that describes a project and its dependencies,
            // or a package-style SPDX document that describes a single (dependency-)package.
            spdxDocument.isProject()
        }.keys.also { remainingFiles ->
            if (remainingFiles.isEmpty()) return definitionFiles

            val discardedFiles = definitionFiles - remainingFiles
            if (discardedFiles.isNotEmpty()) {
                log.info {
                    "Discarded the following ${discardedFiles.size} non-project SPDX files: " +
                            discardedFiles.joinToString { "'$it'" }
                }
            }
        }.toList()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val transitiveDocument = SpdxResolvedDocument.load(spdxDocumentCache, definitionFile, managerName)
        val spdxDocument = transitiveDocument.rootDocument.document

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
            createScope(transitiveDocument, projectPackage.spdxId, type, packages)
        }

        scopes += Scope(
            name = DEFAULT_SCOPE_NAME,
            dependencies = getDependencies(projectPackage.spdxId, transitiveDocument, packages)
        )

        val project = Project(
            id = projectPackage.toIdentifier(),
            cpe = projectPackage.locateCpe(),
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
